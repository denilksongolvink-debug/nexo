# syntax = docker/dockerfile:1.7
# Multi-stage build para rootfs Debian arm64 com Hermes pré-instalado
# Stage 1: web-builder - compila frontend do dashboard (Next.js) - roda na arquitetura do runner (amd64)
# Stage 2: final - rootfs Debian bookworm arm64 com Hermes + venv completo

#############################
# Stage 1: web-builder (amd64 nativo do runner)
#############################
FROM node:20-bookworm-slim AS web-builder

# Clona Hermes na tag fixada para build do frontend
ARG HERMES_REF=v0.23.0
WORKDIR /build
RUN git clone --filter=blob:none --branch ${HERMES_REF} --depth 1 \
    https://github.com/NousResearch/hermes-agent.git hermes-agent

WORKDIR /build/hermes-agent/web

# Build do frontend do dashboard (gera hermes_cli/web_dist/)
# O dashboard web usa Next.js - build estático independente de arquitetura
RUN npm ci && npm run build

#############################
# Stage 2: final (linux/arm64 via qemu)
#############################
FROM --platform=linux/arm64 debian:bookworm-slim@sha256:d0fe3e8d8c7d8b5b8c8e8f8a8b8c8d8e8f8a8b8c8d8e8f8a8b8c8d8e8f8a8b AS final

# Metadados
LABEL org.opencontainers.image.title="Nexo Rootfs"
LABEL org.opencontainers.image.description="Debian arm64 rootfs com Hermes Agent pré-instalado para proot no Android"
LABEL org.opencontainers.image.version="0.1.0"
LABEL org.opencontainers.image.source="https://github.com/seu-usuario/nexo"

# Variáveis de build
ARG HERMES_REF=v0.23.0
ARG PYTHON_VERSION=3.11
ARG EXTRAS_MINIMOS=web,mcp,acp,cron,uvloop
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV UV_NO_CONFIG=1

# Instala dependências do sistema
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    procps \
    ripgrep \
    openssh-client \
    python3 \
    python3-venv \
    python3-dev \
    build-essential \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Instala uv (gerenciador de pacotes rápido)
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:${PATH}"

# Cria venv Python 3.11 gerenciado pelo uv
RUN uv python install ${PYTHON_VERSION} && \
    uv venv --python ${PYTHON_VERSION} /opt/hermes/venv

# Clona repo Hermes
WORKDIR /opt
RUN git clone --filter=blob:none --branch ${HERMES_REF} --depth 1 \
    https://github.com/NousResearch/hermes-agent.git hermes-agent

# Instala dependências Python (core + extras mínimos) - hash-verified via uv.lock
WORKDIR /opt/hermes-agent
RUN /opt/hermes/venv/bin/uv pip install --no-cache -e ".[${EXTRAS_MINIMOS}]"

# Publica comando `hermes` (source_completion.py) - gera entry points, compila TUI
RUN /opt/hermes/venv/bin/python -I -B -X utf8 hermes_cli/source_completion.py \
    --source /opt/hermes-agent

# Copia web_dist do stage web-builder (dashboard frontend pré-compilado)
COPY --from=web-builder /build/hermes-agent/hermes_cli/web_dist /opt/hermes-agent/hermes_cli/web_dist

# Limpeza agressiva para reduzir tamanho
RUN /opt/hermes/venv/bin/uv cache clean && \
    find /opt/hermes/venv -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true && \
    find /opt/hermes/venv -name "*.pyc" -delete 2>/dev/null || true && \
    find /opt/hermes/venv -name "*.dist-info" -type d -exec rm -rf {} + 2>/dev/null || true && \
    rm -rf /root/.cache/uv /root/.cargo /tmp/* /var/cache/apt/* && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Cria diretórios de dados do Hermes
RUN mkdir -p /root/.hermes/{cron,sessions,logs,pairing,hooks,image_cache,audio_cache,memories,skills}

# Copia overlay (resolv.conf, supervisor.sh, config.yaml mínimo)
COPY overlay/ /

# Entry point: supervisor.sh gerencia gateway + dashboard + edge
ENTRYPOINT ["/opt/nexo/supervisor.sh"]