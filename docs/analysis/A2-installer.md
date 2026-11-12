# T1.2 — Instalador (A2-installer.md)

## Modo não-interativo

O `install.sh` suporta `--non-interactive` (ou `--skip-setup`) que:
- Pula a etapa `setup` (wizard `hermes setup` interativo para API keys)
- Pula a etapa `gateway` (instalação do systemd service `hermes gateway install`)
- **Não pula** a etapa `products` (build do CLI, TUI, dashboard web)
- **Não pula** `python-deps` (instalação via `pm.cli install`)

Para pular browser/Playwright: `--skip-browser` (salva preferência, desabilita `agent-browser` no PM)
Para pular computer-use: `--skip-computer-use` (desabilita `cua-driver` no PM)

## Onde instala (modo root)

- Código: `/usr/local/lib/hermes-agent` (ou `$HERMES_HOME/hermes-agent` com `HERMES_HOME=/root/.hermes`)
- Dados: `/root/.hermes` (`HERMES_HOME`)
- Binário `hermes`: `~/.local/bin/hermes` (adicionado ao PATH via shell rc)

## Receita determinística para CI (Debian arm64 glibc)

**Objetivo:** Reproduzir exatamente o que `install.sh --non-interactive --skip-browser --skip-computer-use` faria, mas de forma controlada, sem sudo, sem shell rc, sem systemd.

### Passos (para Dockerfile stage `final`):

```bash
# 1. Pré-requisitos do sistema
apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git procps ripgrep openssh-client \
    python3.11 python3.11-venv python3.11-dev \
    build-essential pkg-config libssl-dev

# 2. Instalar uv (gerenciador de pacotes rápido, substitui pip)
curl -LsSf https://astral.sh/uv/install.sh | sh
export PATH="$HOME/.local/bin:$PATH"

# 3. Clonar repo na tag fixada
git clone --filter=blob:none --branch "$HERMES_REF" --depth 1 \
    https://github.com/NousResearch/hermes-agent.git /opt/hermes-agent
cd /opt/hermes-agent

# 4. Criar venv Python 3.11 gerenciado pelo uv
uv python install 3.11
uv venv --python 3.11 /opt/hermes/venv

# 5. Instalar dependências core + extras mínimos (hash-verified via uv.lock)
# EXTRAS_MINIMOS=web,mcp,acp,cron,uvloop (de A1-extras.md)
# --no-cache para reduzir tamanho da layer
/opt/hermes/venv/bin/uv pip install --no-cache -e ".[web,mcp,acp,cron,uvloop]"

# 6. Build do frontend do dashboard (necessário se dashboard exige web_dist)
# O extra 'web' instala fastapi/uvicorn/starlette mas NÃO roda o build do frontend.
# O build do frontend (Next.js) requer Node.js e é feito em stage separado (stage `web`).
# Ver T1.4-Q3 e T3.1.

# 7. Publicar comando `hermes` (source_completion) — gera entry points, compila TUI, etc.
/opt/hermes/venv/bin/python -I -B -X utf8 hermes_cli/source_completion.py \
    --source /opt/hermes-agent

# 8. Preparar config mínima (diretórios, .env, config.yaml)
mkdir -p /root/.hermes/{cron,sessions,logs,pairing,hooks,image_cache,audio_cache,memories,skills}
touch /root/.hermes/.env && chmod 600 /root/.hermes/.env
cp /opt/hermes-agent/cli-config.yaml.example /root/.hermes/config.yaml

# 9. Limpeza agressiva para reduzir tamanho
/opt/hermes/venv/bin/uv cache clean
find /opt/hermes/venv -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find /opt/hermes/venv -name "*.pyc" -delete 2>/dev/null || true
find /opt/hermes/venv -name "*.dist-info" -type d -exec rm -rf {} + 2>/dev/null || true
rm -rf /root/.cache/uv /root/.cargo /tmp/* /var/cache/apt/*
apt-get clean && rm -rf /var/lib/apt/lists/*
```

### Variáveis de ambiente necessárias no runtime (supervisor.sh):

```bash
export HERMES_HOME=/root/.hermes
export API_SERVER_ENABLED=true
export API_SERVER_HOST=127.0.0.1
export API_SERVER_PORT=8642
export API_SERVER_KEY="${NEXO_API_KEY}"  # injetado pelo runtime.env
export HERMES_DASHBOARD_SESSION_TOKEN="${NEXO_DASH_TOKEN}"  # [VERIFICAR T1.4-Q1]
```

### Diferenças vs install.sh oficial:

| Aspecto | install.sh | Receita CI (rootfs) |
|---------|------------|---------------------|
| Python | Auto-detecta via `pm/lock.json` (3.14 no host) | **Fixado em 3.11** (Debian bookworm default, wheels manylinux2014) |
| uv | Baixa via script | Baixa via script (mesmo) |
| Extras | `pm.cli install` (lazy, interativo) | `uv pip install -e ".[extras]"` (eager, determinístico) |
| Browser/Playwright | `--skip-browser` | **Não instalado** (extra não incluído) |
| Computer-use | `--skip-computer-use` | **Não instalado** |
| Desktop app | `--include-desktop` | **Não incluído** |
| systemd gateway | `hermes gateway install` | **Não** (supervisor.sh gerencia processos) |
| Shell PATH | `wire_shell_path` | **Não** (binário chamado via path absoluto) |
| Config | Copia examples | Copia examples + overlay customizado |

### Verificação pós-instalação:

```bash
/opt/hermes/venv/bin/hermes --version  # deve imprimir v0.23.0
/opt/hermes/venv/bin/hermes doctor     # sem erros fatais
/opt/hermes/venv/bin/python -c "import hermes_cli; import fastapi; import uvicorn; import mcp; print('ok')"
```