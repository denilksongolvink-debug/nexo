#!/bin/bash
# /opt/nexo/supervisor.sh
# Gerencia os 3 processos do backend no proot:
# 1. hermes gateway run      (porta 8642 - API server, cron, plataformas)
# 2. hermes dashboard        (porta 9119 - REST admin, SPA, WebSocket TUI)
# 3. nexo_edge.py            (porta $NEXO_EDGE_PORT - proxy reverso + estático)

set -euo pipefail

# Carrega segredos do runtime.env (gerado pelo Kotlin a cada boot)
# NUNCA logar estas variáveis!
set -a
source /root/.nexo/runtime.env
set +a

export HERMES_HOME=/root/.hermes
export API_SERVER_ENABLED=true
export API_SERVER_HOST=127.0.0.1
export API_SERVER_PORT=8642
export API_SERVER_KEY="${NEXO_API_KEY}"

# Token do dashboard - confirmado em T1.4-Q1
export HERMES_DASHBOARD_SESSION_TOKEN="${NEXO_DASH_TOKEN}"

# Força modo headless para dashboard (sem browser)
export HERMES_SERVE_HEADLESS=1

# Diretório do edge server
export NEXO_EDGE_DIR=/opt/nexo

# Função de cleanup ao receber SIGTERM/SIGINT
stop() {
    echo "[supervisor] Recebido sinal de parada, encerrando processos..."
    kill -TERM ${GW_PID:-0} ${DB_PID:-0} ${EDGE_PID:-0} 2>/dev/null
    # Aguarda até 10s para shutdown gracioso
    local count=0
    while kill -0 ${GW_PID:-0} 2>/dev/null || kill -0 ${DB_PID:-0} 2>/dev/null || kill -0 ${EDGE_PID:-0} 2>/dev/null; do
        sleep 0.5
        count=$((count + 1))
        if [ $count -ge 20 ]; then
            echo "[supervisor] Timeout, forçando kill -9"
            kill -9 ${GW_PID:-0} ${DB_PID:-0} ${EDGE_PID:-0} 2>/dev/null
            break
        fi
    done
    echo "[supervisor] Todos os processos encerrados"
    exit 0
}

trap stop TERM INT

echo "[supervisor] Iniciando Hermes Gateway (porta 8642)..."
/opt/hermes/venv/bin/hermes gateway run &
GW_PID=$!

echo "[supervisor] Iniciando Hermes Dashboard (porta 9119)..."
/opt/hermes/venv/bin/hermes dashboard --no-open --host 127.0.0.1 --port 9119 &
DB_PID=$!

# Aguarda gateway e dashboard ficarem prontos
echo "[supervisor] Aguardando serviços ficarem prontos..."
for i in {1..30}; do
    if curl -sf http://127.0.0.1:8642/health >/dev/null 2>&1 && \
       curl -sf -H "X-Hermes-Session-Token: ${NEXO_DASH_TOKEN}" http://127.0.0.1:9119/api/config >/dev/null 2>&1; then
        echo "[supervisor] Gateway e Dashboard prontos"
        break
    fi
    sleep 1
    if [ $i -eq 30 ]; then
        echo "[supervisor] ERRO: Timeout aguardando serviços"
        stop
    fi
done

echo "[supervisor] Iniciando Nexo Edge (porta ${NEXO_EDGE_PORT})..."
cd /opt/nexo
/opt/hermes/venv/bin/python nexo_edge.py &
EDGE_PID=$!

echo "[supervisor] Todos os processos iniciados (GW:$GW_PID, DB:$DB_PID, EDGE:$EDGE_PID)"

# wait -n: se QUALQUER filho morrer, derruba tudo
# O BackendService do Kotlin detecta via healthz e reinicia o supervisor
wait -n
echo "[supervisor] Um processo filho encerrou inesperadamente, derrubando todos"
stop