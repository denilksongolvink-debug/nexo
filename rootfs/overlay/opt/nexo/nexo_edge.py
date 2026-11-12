#!/usr/bin/env python3
"""
nexo_edge.py - Servidor estático + proxy reverso com autenticação
Roda na porta $NEXO_EDGE_PORT (default 8700), serve Nexo estático + proxy /hx/api/* e /hx/dash/*

Dependências: apenas httpx (já no core do Hermes)
"""

import os
import secrets
import hmac
import asyncio
from pathlib import Path
from typing import Optional

import httpx
from starlette.applications import Starlette
from starlette.responses import (
    Response, HTMLResponse, JSONResponse, StreamingResponse, FileResponse, RedirectResponse
)
from starlette.routing import Route, Mount
from starlette.staticfiles import StaticFiles
from starlette.middleware import Middleware
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.datastructures import Headers, MutableHeaders

# Config via env (injetado pelo Kotlin via runtime.env)
NEXO_API_KEY = os.environ["NEXO_API_KEY"]
NEXO_DASH_TOKEN = os.environ["NEXO_DASH_TOKEN"]
NEXO_EDGE_SECRET = os.environ["NEXO_EDGE_SECRET"]
NEXO_EDGE_NONCE = os.environ["NEXO_EDGE_NONCE"]
NEXO_EDGE_PORT = int(os.environ.get("NEXO_EDGE_PORT", "8700"))
WEB_DIR = Path("/opt/nexo/web")

# Backend targets
GATEWAY_URL = "http://127.0.0.1:8642"
DASHBOARD_URL = "http://127.0.0.1:9119"

# Cookie settings
COOKIE_NAME = "nexo_s"
COOKIE_MAX_AGE = 60 * 60 * 24 * 30  # 30 dias

# ---------- Auth / Cookie handling ----------

def verify_cookie(cookie_value: str) -> bool:
    """Verifica HMAC do cookie"""
    if not cookie_value:
        return False
    try:
        parts = cookie_value.split('.')
        if len(parts) != 2:
            return False
        payload, sig = parts
        expected = hmac.new(
            NEXO_EDGE_SECRET.encode(), payload.encode(), 'sha256'
        ).hexdigest()
        return hmac.compare_digest(sig, expected)
    except Exception:
        return False

def make_cookie(nonce: str) -> str:
    """Cria cookie assinado"""
    sig = hmac.new(NEXO_EDGE_SECRET.encode(), nonce.encode(), 'sha256').hexdigest()
    return f"{nonce}.{sig}"

# ---------- Middleware de autenticação ----------

class AuthMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        path = request.url.path
        
        # Rotas públicas
        if path == "/healthz" or path.startswith("/hx/api/") or path.startswith("/hx/dash/"):
            # Estas rotas fazem proxy e injetam auth server-side
            return await call_next(request)
        
        # Rota de bootstrap com nonce
        if path == "/" and request.query_params.get("k") == NEXO_EDGE_NONCE:
            response = await call_next(request)
            # Seta cookie
            cookie_val = make_cookie(secrets.token_urlsafe(32))
            response.set_cookie(
                COOKIE_NAME, cookie_val,
                httponly=True, secure=False, samesite="strict",
                max_age=COOKIE_MAX_AGE, path="/"
            )
            return response
        
        # Verifica cookie para todas as outras rotas
        cookie = request.cookies.get(COOKIE_NAME)
        if not verify_cookie(cookie):
            return JSONResponse({"error": "unauthorized"}, status_code=403)
        
        return await call_next(request)

# ---------- Proxy handlers ----------

async def proxy_gateway(request):
    """Proxy para /hx/api/* -> gateway (porta 8642) com Bearer injectado"""
    path = request.url.path.replace("/hx/api", "", 1)
    url = f"{GATEWAY_URL}{path}"
    if request.url.query:
        url += f"?{request.url.query}"
    
    # Headers: remove auth do cliente, injeta Bearer do edge
    headers = dict(request.headers)
    headers.pop("authorization", None)
    headers.pop("cookie", None)
    headers["authorization"] = f"Bearer {NEXO_API_KEY}"
    headers["host"] = "127.0.0.1:8642"
    
    body = await request.body()
    
    async with httpx.AsyncClient(timeout=None) as client:
        req = client.build_request(
            request.method, url, headers=headers, content=body
        )
        resp = await client.send(req, stream=True)
        
        # Stream response
        return StreamingResponse(
            resp.aiter_bytes(),
            status_code=resp.status_code,
            headers=dict(resp.headers),
            media_type=resp.headers.get("content-type")
        )

async def proxy_dashboard(request):
    """Proxy para /hx/dash/* -> dashboard (porta 9119) com X-Hermes-Session-Token"""
    path = request.url.path.replace("/hx/dash", "", 1)
    url = f"{DASHBOARD_URL}{path}"
    if request.url.query:
        url += f"?{request.url.query}"
    
    headers = dict(request.headers)
    headers.pop("authorization", None)
    headers.pop("cookie", None)
    headers.pop("x-hermes-session-token", None)
    headers["x-hermes-session-token"] = NEXO_DASH_TOKEN
    headers["host"] = "127.0.0.1:9119"
    headers.pop("origin", None)  # Remove Origin para passar CORS do dashboard
    
    body = await request.body()
    
    async with httpx.AsyncClient(timeout=None) as client:
        req = client.build_request(
            request.method, url, headers=headers, content=body
        )
        resp = await client.send(req, stream=True)
        
        # Remove headers de auth da resposta
        resp_headers = dict(resp.headers)
        resp_headers.pop("set-cookie", None)
        
        return StreamingResponse(
            resp.aiter_bytes(),
            status_code=resp.status_code,
            headers=resp_headers,
            media_type=resp.headers.get("content-type")
        )

async def healthz(request):
    """Health check agregado"""
    gateway_ok = False
    dashboard_ok = False
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            r = await client.get(f"{GATEWAY_URL}/health")
            gateway_ok = r.status_code == 200
    except Exception:
        pass
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            r = await client.get(
                f"{DASHBOARD_URL}/api/config",
                headers={"x-hermes-session-token": NEXO_DASH_TOKEN}
            )
            dashboard_ok = r.status_code == 200
    except Exception:
        pass
    
    return JSONResponse({
        "edge": True,
        "gateway": gateway_ok,
        "dashboard": dashboard_ok
    })

async def serve_spa(request):
    """Serve arquivos estáticos do Nexo (fallback para index.html)"""
    path = request.url.path.lstrip("/")
    if not path:
        path = "index.html"
    
    file_path = WEB_DIR / path
    if file_path.is_file():
        return FileResponse(file_path)
    
    # Fallback para SPA routing
    index_path = WEB_DIR / "index.html"
    if index_path.is_file():
        return FileResponse(index_path)
    
    return HTMLResponse(
        "<h1>Nexo not built</h1><p>Run web build first</p>",
        status_code=404
    )

# ---------- App setup ----------

routes = [
    Route("/healthz", healthz, methods=["GET"]),
    Route("/hx/api/{path:path}", proxy_gateway, methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"]),
    Route("/hx/dash/{path:path}", proxy_dashboard, methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"]),
    Route("/{path:path}", serve_spa, methods=["GET", "HEAD"]),
]

middleware = [
    Middleware(AuthMiddleware),
]

app = Starlette(routes=routes, middleware=middleware)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=NEXO_EDGE_PORT, log_level="info")