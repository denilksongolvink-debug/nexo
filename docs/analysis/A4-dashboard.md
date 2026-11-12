# T1.4 — Backend do Dashboard e Token de Sessão (A4-dashboard.md)

## Q1: De onde vem o token do dashboard em loopback?

**Resposta:** A variável de ambiente `HERMES_DASHBOARD_SESSION_TOKEN` define o token usado no header `X-Hermes-Session-Token` (legacy Bearer também aceito).

- Se **não definida**: gera token aleatório a cada start (`secrets.token_urlsafe(32)`) — morre com o processo
- Se **definida como valor literal**: usa esse valor
- Se **definida como `file:<path>`**: lê o token do arquivo (para deployment managers: agenix, sops-nix, systemd credentials)

O token é resolvido em `hermes_cli/web_server_session_token.py:resolve_session_token()` e armazenado em `_SESSION_TOKEN` (module global).

## Q2: GET / em 127.0.0.1:9119 vaza o token no HTML?

**SIM — Vaza no modo loopback (padrão).**

Teste real:
```bash
HERMES_DASHBOARD_SESSION_TOKEN=test-token-123 hermes dashboard --no-open --host 127.0.0.1 --port 9119 --skip-build
curl -s 127.0.0.1:9119/ | grep -i token
```
Resultado: `<script>window.__HERMES_SESSION_TOKEN__="OMidw-Bk8PLQGdaqWp8mFM0MTm5ZcGi1VYKxGFzXSyU";...</script>`

**Por que vaza:**
- Em `web_server_dashboard.py:_serve_index()`, quando `auth_required=False` (modo loopback) E `withhold_session_token=False`, injeta:
  ```python
  token_js = f'window.__HERMES_SESSION_TOKEN__="{_server()._SESSION_TOKEN}";'
  ```
- O header `X-Hermes-Session-Token` é validado em `_has_valid_session_token()` comparando com `_SESSION_TOKEN`

**Quando NÃO vaza:**
1. **Modo gated (OAuth/Basic Auth)** — `auth_required=True` → `withheld=True` → token NÃO injetado (usa cookie auth)
2. **Webapp hospedado pelo gateway** — `withhold_session_token=True` forçado em `GatewayDashboardMount._bind_state()`
3. **`hermes serve` (headless)** — só injeta se `auth_required=False` E `withhold_session_token=False`

**IMPACTO DE SEGURANÇA CRÍTICO:** No Android, **qualquer app pode conectar em 127.0.0.1** (loopback é compartilhado entre UIDs). Se o dashboard rodar em loopback no modo padrão, **outro app lê o token do HTML e acessa TODAS as APIs de admin** (`/api/config`, `/api/env`, `/api/skills`, `/api/mcp`, `/api/logs`, etc.).

## Q3: Dashboard sobe sem `hermes_cli/web_dist`?

**Sim, mas retorna 404 JSON** — "Frontend not built. Run: cd web && npm run build".

O build do frontend (Next.js + TypeScript) **requer Node.js/pnpm**. No celular (rootfs Debian arm64) isso é **inaceitável** (pesado, lento, binários x86 via box64).

**Solução:** O CI **deve gerar** `web_dist` no stage `web` (multi-arch: JS é independente de arquitetura) e copiar para o rootfs final.

Comando de build: `cd web && npm run build` (gera `hermes_cli/web_dist/`)

## Q4: Guards (Host, Origin, peer IP) — o que o edge precisa enviar?

| Guard | Comportamento | O que edge deve fazer |
|-------|---------------|----------------------|
| **Host header** | Validado contra `app.state.bound_host` (DNS rebinding protection, GHSA-ppp5-vxwm-4cf7) | Ajustar `Host` para `127.0.0.1:9119` |
| **Origin** | CORS via `_LocalOriginCORSMiddleware` — só permite trusted loopback origins (`is_trusted_local_origin`) | **Remover `Origin`** header completamente |
| **Peer IP** | Não há verificação explícita além de loopback (127.0.0.1) | N/A (já é loopback) |
| **Cookie** | Modo gated usa cookie; modo loopback usa header/token | **Remover `Cookie`** header |
| **Authorization / X-Hermes-Session-Token** | Validados pelo dashboard | **Remover** headers de auth vindos do cliente; injetar o token correto do edge |

## Q5: Flag para escutar em Unix socket?

**NÃO EXISTE.** O uvicorn suporta `--uds` mas não é exposto no CLI do Hermes (`dashboard.py`, `serve.py`, `web_server.py`). Ouvinte só em TCP (127.0.0.1:porta).

## Q6: Comando para build do frontend?

`cd web && npm run build` (conforme mensagem de erro no código e docs).
Gera `hermes_cli/web_dist/` com SPA estático (HTML/JS/CSS/assets).

---

## DECISÃO D4 (registrada em DECISIONS.md)

```markdown
## D4 — segurança do dashboard
escolha: token forte via env + dashboard só ligado quando tela de Configurações aberta (on-demand)
motivo: Q2 confirmado — token vaza no HTML em loopback. No Android, qualquer app lê 127.0.0.1:9119. Duas mitigações:
  1. Token forte (32 bytes urlsafe) gerado a cada boot do app, injetado via runtime.env — não hardcoded
  2. Dashboard NÃO roda sempre. Só sobe quando usuário abre tela de Configurações (BackendService inicia dashboard sob demanda). Fecha ao sair da tela.
  - Fallback: se on-demand falhar, usar plugin Hermes (~/.hermes/plugins/) para expor rotas admin sem dashboard (docs Extending the Dashboard).
gatilho para reverter: se dashboard for necessário para funcionalidades core (ex: WebSocket TUI /api/pty que o Nexo usar), reavaliar com Unix socket (não suportado hoje) ou proxy com token injetado server-side.
```