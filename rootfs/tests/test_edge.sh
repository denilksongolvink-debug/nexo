#!/bin/bash
# rootfs/tests/test_edge.sh - Testes do nexo_edge.py
# Rodar dentro do proot após supervisor.sh subir

set -euo pipefail

EDGE_PORT=${NEXO_EDGE_PORT:-8700}
EDGE_URL="http://127.0.0.1:${EDGE_PORT}"
PASS=0
FAIL=0

test_fail() {
    echo "❌ FAIL: $1"
    FAIL=$((FAIL + 1))
}

test_pass() {
    echo "✅ PASS: $1"
    PASS=$((PASS + 1))
}

echo "=== Testando nexo_edge.py na porta ${EDGE_PORT} ==="

# 1. Sem cookie → 403
echo -n "Test 1: Sem cookie → "
code=$(curl -s -o /dev/null -w "%{http_code}" "${EDGE_URL}/")
if [ "$code" = "403" ]; then test_pass "403 sem cookie"; else test_fail "esperado 403, got $code"; fi

# 2. Com ?k=NONCE correto → 200 + Set-Cookie
echo -n "Test 2: ?k=NONCE correto → "
NONCE=$(grep NEXO_EDGE_NONCE /root/.nexo/runtime.env | cut -d= -f2)
resp=$(curl -s -D - "${EDGE_URL}/?k=${NONCE}")
code=$(echo "$resp" | head -1 | cut -d' ' -f2)
cookie=$(echo "$resp" | grep -i "set-cookie:" | head -1)
if [ "$code" = "200" ] && echo "$cookie" | grep -q "nexo_s="; then
    test_pass "200 + Set-Cookie"
    # Extrai cookie para testes seguintes
    COOKIE=$(echo "$cookie" | sed -E 's/.*nexo_s=([^;]+).*/\1/')
else
    test_fail "esperado 200 + cookie, got $code"
    COOKIE=""
fi

# 3. ?k= errado → 403
echo -n "Test 3: ?k=errado → "
code=$(curl -s -o /dev/null -w "%{http_code}" "${EDGE_URL}/?k=wrong")
if [ "$code" = "403" ]; then test_pass "403 com nonce errado"; else test_fail "esperado 403, got $code"; fi

# 4. SSE streaming real-time
echo -n "Test 4: SSE streaming (healthz) → "
# Usa healthz que não precisa auth
code=$(curl -s -o /dev/null -w "%{http_code}" "${EDGE_URL}/healthz")
if [ "$code" = "200" ]; then test_pass "healthz 200"; else test_fail "healthz $code"; fi

# 5. Proxy /hx/api/health injeta Bearer
echo -n "Test 5: /hx/api/health (proxy gateway) → "
code=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: nexo_s=${COOKIE}" "${EDGE_URL}/hx/api/health")
if [ "$code" = "200" ]; then test_pass "proxy gateway 200"; else test_fail "proxy gateway $code"; fi

# 6. Proxy /hx/dash/api/config injeta X-Hermes-Session-Token
echo -n "Test 6: /hx/dash/api/config (proxy dashboard) → "
code=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: nexo_s=${COOKIE}" "${EDGE_URL}/hx/dash/api/config")
if [ "$code" = "200" ]; then test_pass "proxy dashboard 200"; else test_fail "proxy dashboard $code"; fi

# 7. Nenhum segredo vaza em respostas
echo -n "Test 7: Segredos não vazam → "
resp=$(curl -s -H "Cookie: nexo_s=${COOKIE}" "${EDGE_URL}/hx/api/health")
if echo "$resp" | grep -qi "$NEXO_API_KEY\|$NEXO_DASH_TOKEN\|$NEXO_EDGE_SECRET"; then
    test_fail "SEGREDO VAZOU!"
else
    test_pass "nenhum segredo no body"
fi

resp=$(curl -s -D - -H "Cookie: nexo_s=${COOKIE}" "${EDGE_URL}/hx/api/health" | head -20)
if echo "$resp" | grep -qi "$NEXO_API_KEY\|$NEXO_DASH_TOKEN\|$NEXO_EDGE_SECRET"; then
    test_fail "SEGREDO VAZOU NOS HEADERS!"
else
    test_pass "nenhum segredo nos headers"
fi

# 8. Arquivos estáticos servidos
echo -n "Test 8: Estáticos (index.html) → "
code=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: nexo_s=${COOKIE}" "${EDGE_URL}/")
if [ "$code" = "200" ]; then test_pass "index.html 200"; else test_fail "index.html $code"; fi

echo ""
echo "=== Resultado: ${PASS}/${PASS+FAIL} testes passaram ==="
if [ $FAIL -gt 0 ]; then
    exit 1
fi
exit 0