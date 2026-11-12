# T1.1 — Extras e Dependências (A1-extras.md)

## (a) Extras disponíveis (de pyproject.toml)

| Extra | Descrição | Peso/Nota |
|-------|-----------|-----------|
| `uvloop` | Event loop de alta performance (libuv). **Não roda no Android/Termux** (bionic libc). | Desktop/server only |
| `anthropic` | Provider nativo Anthropic | Opt-in |
| `exa` | Web search via Exa | Opt-in |
| `firecrawl` | Web search via Firecrawl | Opt-in |
| `parallel-web` | Web search paralelo | Opt-in |
| `ddgs` | Web search via DuckDuckGo | Opt-in |
| `fal` | Image generation via Fal.ai | Opt-in |
| `edge-tts` | TTS via Edge (default) | Opt-in |
| `neutts` | NeuTTS (requer Python < 3.14) | Python 3.14+ bloqueado |
| `kittentts` | KittenTTS (sem ARM64 Windows, sem macOS x64, sem Android) | Plataforma limitada |
| `piper` | Piper TTS (sem ARM64 Windows, sem macOS x64, sem Android) | Plataforma limitada |
| `modal` | Modal.com sandbox | Opt-in |
| `daytona` | Daytona sandbox | Opt-in |
| `vercel` | Vercel sandbox | Opt-in |
| `google-meet` | Google Meet (Playwright - sem Android) | Opt-in |
| `messaging` | Telegram, Discord, Slack, aiohttp | Opt-in |
| `cron` | **Agora vazio** (croniter movido para core) | Back-compat |
| `slack` | Slack (aiohttp) | Opt-in |
| `matrix` | Matrix gateway (mautrix + python-olm) - **Linux only** | Heavy, linux-only |
| `wecom` | WeCom callback (defusedxml) | Opt-in |
| `keychain` | OS keychain storage (keyring) | Opt-in, lazy |
| `tts-premium` | ElevenLabs TTS | Opt-in |
| `voice` | STT local (faster-whisper + ctranslate2/onnxruntime) - **sem Android, sem Windows ARM64, sem macOS x64** | Heavy, platform-limited |
| `wake` | Wake word engines (pyopen-wakeword, sherpa-onnx, pvporcupine) | Heavy |
| `telegram` | Telegram bot | Opt-in, lazy |
| `discord` | Discord bot | Opt-in, lazy |
| `stt-whisper` | Subset de voice | Opt-in |
| `audio-io` | sounddevice + numpy | Opt-in |
| `silk` | WeChat voice decode | Opt-in |
| `wake-openwakeword` | Subset de wake | Opt-in |
| `wake-sherpa` | Subset de wake | Opt-in |
| `bedrock` | AWS Bedrock provider | Opt-in |
| `vertex` | Google Vertex AI | Opt-in |
| `azure-identity` | Azure OpenAI | Opt-in |
| `termux` | **Baseline Android/Termux**: telegram, cron, mcp, acp | Tested path |
| `termux-all` | Termux "tudo": termux + google + sms + web + pty | Best-effort |
| `dingtalk` | DingTalk | Opt-in |
| `feishu` | Feishu/Lark | Opt-in |
| `google` | Google Workspace skills | Opt-in |
| `youtube` | YouTube transcript | Opt-in |
| `web` | **Dashboard (FastAPI + Uvicorn + Starlette + python-multipart)** | **CRÍTICO** |
| `mcp` | MCP client (mcp 2.3.0, httpx2, starlette) | **CRÍTICO** |
| `acp` | Agent Client Protocol | **CRÍTICO** |
| `sms` | SMS (aiohttp) | Opt-in |
| `teams` | Microsoft Teams | Opt-in |
| `computer-use` | Computer use via MCP (mcp, httpx2, starlette) | Opt-in |
| `doc-extract` | Firecrawl anydoc | Opt-in |
| `documents` | Office/PDF authoring (python-docx, openpyxl, etc) | Opt-in, ~25MB |
| `documents-pdf` | PDF extraction (pdfplumber, pypdfium2) | Opt-in, ~17MB |
| `trace-upload` | HuggingFace Hub | Opt-in |
| `vision` | No-op (Pillow agora é core) | Back-compat |
| `pty` | No-op (ptyprocess/pywinpty agora core) | Back-compat |
| `nemo-relay` | No-op | Back-compat |
| `mistral` | Mistral STT/TTS | Opt-in, lazy |
| `otlp` | OpenTelemetry OTLP export | Opt-in, lazy |

## (b) Conjunto MÍNIMO para gateway + API server + dashboard + cron + MCP

**EXTRAS_MINIMOS=web,mcp,acp,cron,uvloop**

Justificativa:
- `web` — FastAPI + Uvicorn + Starlette para o dashboard REST API e SPA
- `mcp` — MCP client (necessário para ferramentas MCP)
- `acp` — Agent Client Protocol (core para integração)
- `cron` — Agendamentos (embora vazio, mantém compatibilidade; croniter é core)
- `uvloop` — Performance no gateway (roda em Debian glibc arm64, **não** Android/bionic)

**Extras NÃO incluídos (pesados/inúteis no APK):**
- `voice`, `wake` — STT/TTS local (ctranslate2, onnxruntime, sounddevice) — sem wheels Android/ARM64 completos
- `messaging`, `telegram`, `discord`, `slack`, `matrix`, `dingtalk`, `feishu`, `teams`, `wecom` — plataformas de mensageria
- `google`, `google-meet`, `youtube` — Google workspace
- `modal`, `daytona`, `vercel`, `computer-use` — sandboxes externos
- `documents`, `documents-pdf` — Office/PDF authoring (~42MB)
- `bedrock`, `vertex`, `azure-identity` — providers cloud
- `tts-premium`, `edge-tts`, `neutts`, `kittentts`, `piper`, `mistral` — TTS providers
- `exa`, `firecrawl`, `parallel-web`, `ddgs`, `fal` — search/image providers
- `keychain` — OS keychain (não aplicável no rootfs)
- `otlp` — Observabilidade (opcional)
- `trace-upload` — HuggingFace (opcional)
- `silk` — WeChat voice
- `stt-whisper`, `audio-io` — subset de voice

## (c) Lib HTTP assíncrona já instalada (para nexo_edge.py)

**HTTP_LIB=httpx** (versão 0.28.1 com `[socks]`, pinned em `dependencies` core)

Também disponível:
- `httpx2==2.15.1` (no extra `mcp` e `computer-use` — MCP SDK migration)
- `aiohttp==3.14.5` (em extras `messaging`, `slack`, `matrix`, `sms`, `teams` — **não** no core mínimo)
- `requests==2.34.2` (sync, em core)

**Recomendação:** Use `httpx` (já no core, async nativo, suporta HTTP/2, WebSockets, SSE streaming). Não adiciona dependência.

## (d) Extras que puxam deps pesadas/inúteis

| Extra | Deps problemáticas | Motivo |
|-------|-------------------|--------|
| `voice` | faster-whisper → ctranslate2, onnxruntime | Sem wheels ARM64 Windows, sem macOS x64, sem Android |
| `wake` | sherpa-onnx, pvporcupine, pyopen-wakeword | Binários nativos pesados, plataforma limitada |
| `messaging` | aiohttp, discord.py[voice], python-telegram-bot | Plataformas de chat não necessárias no APK standalone |
| `matrix` | mautrix[encryption] → python-olm | Linux-only, compilação complexa (libolm archived) |
| `documents` | python-docx, openpyxl, python-pptx, reportlab, pypdf | ~25MB, só para skills de produtividade |
| `documents-pdf` | pdfplumber, pypdfium2 | ~17MB, pdfium nativo |
| `computer-use` | mcp, httpx2, starlette + cua-driver externo | Requer driver nativo separado |
| `google-meet` | playwright (Chromium) | Navegador completo — impossível no celular |
| `modal`, `daytona`, `vercel` | SDKs cloud | Sandboxes externos |