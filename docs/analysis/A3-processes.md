# T1.3 — Entry Points e Processos (A3-processes.md)

## (a) O que faz `hermes serve`

**Resposta:** `hermes serve` é o **backend headless JSON-RPC/WebSocket** que alimenta o **Desktop App** (Tauri) e backends remotos. Ele:
- Roda na porta **9119** (default), host **127.0.0.1**
- **NÃO** abre navegador (`no_open=True`, `headless_backend=True`)
- **NÃO** faz build do frontend web (SPA)
- Compartilha o mesmo handler `cmd_dashboard` → `start_server` que o `hermes dashboard`
- Difere do `dashboard` apenas nas flags: `headless_backend=True`, `no_open=True`

**Não é** o gateway de mensageria nem o dashboard REST API. É o servidor que o app desktop conecta via WebSocket (`/api/ws`) para chat embutido (TUI over PTY).

## (b) Como subir gateway em foreground sem systemd

```bash
hermes gateway run [--verbose] [--quiet] [--replace] [--force] [--idle_exit]
```

- `--replace`: mata instância existente antes de subir (evita loops de restart systemd)
- `--force`: ignora guarda de supervisor (s6/systemd)
- `--idle_exit`: sai quando ocioso (para cliente que auto-iniciou)
- Cria PID file em `$HERMES_HOME/gateway.pid` e lock em `$HERMES_HOME/gateway.lock`
- Logs em `$HERMES_HOME/logs/gateway.log`

**Variáveis de ambiente relevantes:**
- `HERMES_HOME` — diretório de dados (default `~/.hermes`)
- `API_SERVER_ENABLED=true` — habilita API server OpenAI-compatível
- `API_SERVER_HOST=127.0.0.1`, `API_SERVER_PORT=8642`, `API_SERVER_KEY=...`

## (c) API server sobe sozinho com `API_SERVER_ENABLED=true` ao rodar `hermes gateway`?

**SIM.** O gateway (`gateway/run.py` → `gateway/run_bootstrap.py` → `GatewayRunner`) **inicia o API server internamente** como uma "platform" do gateway quando:
1. `API_SERVER_ENABLED=true` (env var ou config.yaml `platforms.api_server.enabled: true`)
2. Configura host/port via `API_SERVER_HOST`, `API_SERVER_PORT` (defaults 127.0.0.1:8642)
3. Requer `API_SERVER_KEY` (Bearer token) — **obrigatório**, senão não sobe

O API server roda **no mesmo processo do gateway** (mesmo event loop), montado como rotas FastAPI/Starlette. Não é processo separado.

## (d) PID/lock files e logs

| Arquivo | Localização | Propósito |
|---------|-------------|-----------|
| `gateway.pid` | `$HERMES_HOME/gateway.pid` | PID do processo gateway (para `hermes gateway stop/status`) |
| `gateway.lock` | `$HERMES_HOME/gateway.lock` | File lock (flock) — impede múltiplas instâncias no mesmo `HERMES_HOME` |
| `gateway_state.json` | `$HERMES_HOME/gateway_state.json` | Estado de saúde (running/degraded/draining/stopped), heartbeat, plataformas |
| `gateway.log` | `$HERMES_HOME/logs/gateway.log` | Logs estruturados do gateway |
| `dashboard.pid` | `$HERMES_HOME/dashboard.pid` | PID do processo dashboard/serve (quando rodam separado) |

## (e) `hermes dashboard` vs `hermes serve` vs `hermes gateway`

| Comando | Processo | Porta | Função |
|---------|----------|-------|--------|
| `hermes dashboard` | Web server (uvicorn) | 9119 | SPA dashboard + REST API admin + WebSocket `/api/ws` + PTY |
| `hermes serve` | Web server (uvicorn) | 9119 | **Mesmo servidor** que dashboard, mas **headless** (sem SPA, sem browser) |
| `hermes gateway run` | Gateway process | 8642 (API server) + control socket | Messaging platforms (Telegram, Discord, etc) + cron + API server OpenAI-compat |

**Conclusão crítica para D2:**
- `hermes serve` **NÃO** une gateway + dashboard. É só o backend headless do desktop app.
- O **gateway** (`hermes gateway run`) é processo **separado** que roda plataformas de mensageria + cron + API server.
- O **dashboard** (`hermes dashboard`) é processo **separado** que serve a UI web + REST API de admin.

**Para o Nexo APK precisamos de 2 processos obrigatórios:**
1. `hermes gateway run` (porta 8642 + control socket) — API server, cron, plataformas
2. `hermes dashboard --no-open --host 127.0.0.1 --port 9119` — REST admin API, WebSocket TUI, SPA (se `web_dist` existir)

O `serve` **não substitui** nenhum dos dois.

## DECISÃO D2 (registrada em DECISIONS.md)

```markdown
## D2 — processos
escolha: 2 processos (hermes gateway run + hermes dashboard --no-open)
motivo: hermes serve é backend headless do desktop app (JSON-RPC/WS na porta 9119), NÃO une gateway+dashboard. O gateway (porta 8642) roda plataformas+cron+API server; o dashboard (porta 9119) roda REST admin+SPA. São independentes e devem ser gerenciados separadamente (supervisor.sh com wait -n).
gatilho para reverter: se futura versão do Hermes unificar tudo em `serve` (improvável dado o código atual).
```