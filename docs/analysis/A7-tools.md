# T1.7 — Tools, Terminal e Aprovações (A7-tools.md)

## Toolsets disponíveis e status no rootfs Debian arm64 (glibc)

| Toolset | Funciona? | Dependência | Default no APK | Notas |
|---------|-----------|-------------|----------------|-------|
| **terminal** | ✅ Sim | `local` (core), `docker` (precisa Docker daemon), `ssh` (precisa SSH), `modal/daytona/vercel` (cloud) | `local` | Backend `local` roda nativo no proot; `docker`/`ssh` precisam config extra |
| **web** (search) | ⚠️ Parcial | `ddgs` (core), `exa`/`firecrawl`/`parallel-web` (extras opt-in) | `ddgs` | `ddgs` é core (sem API key); providers premium precisam extras |
| **files** | ✅ Sim | Core | Habilitado | read_file, write_file, list_dir, etc. |
| **web_fetch** | ✅ Sim | Core (httpx) | Habilitado | HTTP requests |
| **memory** | ✅ Sim | Core (builtin), plugins (chromadb, pinecone, etc) | `builtin` | Built-in não precisa extra; plugins lazy-install |
| **skills** | ✅ Sim | Core | Habilitado | Skills do hub instaladas via dashboard |
| **cron** | ✅ Sim | Core (croniter) | Habilitado | Agendamentos via API server `/api/jobs` |
| **mcp** | ✅ Sim | Extra `mcp` (mcp 2.3.0, httpx2, starlette) | Habilitado | MCP servers stdio/HTTP |
| **delegate** | ✅ Sim | Core | Habilitado | Subagent delegation |
| **execute_code** | ✅ Sim | Core (Python subprocess) | Habilitado | Python execution tool |
| **image_gen** | ⚠️ Parcial | `fal` (extra), `nous` (subscription) | Desabilitado | Precisa API key ou subscription |
| **video_gen** | ⚠️ Parcial | `fal` (extra), `nous` (subscription) | Desabilitado | Precisa API key ou subscription |
| **stt** (voice) | ❌ Não | `faster-whisper` → `ctranslate2`, `onnxruntime` | **Desabilitado** | Sem wheels ARM64 Android; `sounddevice`/`numpy` ok mas STT local falha |
| **tts** | ⚠️ Parcial | `edge-tts` (core), `elevenlabs`/`openai`/etc (extras) | `edge` | `edge-tts` funciona (core); premium precisa extras |
| **browser** | ❌ Não | `playwright` + Chromium | **Desabilitado** | Chromium não roda no Android/proot sem box64; `lightpanda` (local, sem Chromium) pode funcionar |
| **computer_use** | ❌ Não | `cua-driver` (binário nativo macOS/Win/Linux X11) | **Desabilitado** | Binário não disponível para Android arm64 |
| **x_search** | ⚠️ Parcial | `xai` OAuth/API key | Desabilitado | Precisa credencial xAI |
| **vision** | ✅ Sim | Core (Pillow) | Habilitado | Image processing (redimensionamento, OCR marker) |
| **analytics** | ✅ Sim | Core | Habilitado | Métricas de uso |
| **acp** | ✅ Sim | Extra `acp` | Habilitado | Agent Client Protocol |

**Toolsets CONFIG_ONLY (não aparecem no checklist `hermes tools`, só via config):**
- `stt` — Speech-to-text (voice) — desabilitado por padrão no APK

**Toolsets que exigem Node/Chromium/Docker (INDISPONÍVEIS no APK):**
| Toolset | Dependência faltante | Mitigação |
|---------|---------------------|-----------|
| `browser` (Local Browser) | Chromium + Playwright | Desabilitado; `lightpanda` (local, sem Chromium) é alternativa |
| `browser` (Browser Use cloud) | Browser Use API | Desabilitado |
| `computer_use` | cua-driver nativo | Desabilitado |
| `terminal.docker` | Docker daemon | Desabilitado (precisa Docker no host Android — não disponível) |
| `terminal.modal/daytona/vercel` | Cloud accounts | Opcional, só se usuário configurar |

**Como desligar toolsets problemáticas por padrão no APK:**
No `config.yaml` do overlay:
```yaml
agent:
  disabled_toolsets: ["browser", "computer_use", "stt", "image_gen", "video_gen", "x_search"]
```
Ou via env var: `HERMES_AGENT_DISABLED_TOOLSETS=browser,computer_use,stt,image_gen,video_gen,x_search`

---

## Terminal backend `local` (tools/environments/local.py)

- **Spawn-per-call** com session snapshot
- Usa `subprocess.Popen` com PTY (via `ptyprocess` — agora core dependency)
- Temp dir: `$HERMES_HOME/cache/terminal` (persiste no rootfs, pruned 24h idle)
- **Funciona no rootfs Debian arm64** — usa binários nativos do sistema (`/bin/bash`, etc.)
- Policy de env: strip Hermes-internal secrets, mantém first-party env vars

---

## Aprovações (tools/approval.py)

**Fluxo de aprovação:**
1. Tool perigosa detectada → `submit_pending(session_key, approval)` cria pedido
2. Evento `approval.request` emitido via SSE (`/v1/runs/{id}/events`)
3. Cliente responde: `POST /v1/runs/{run_id}/approval` com:
   ```json
   {
     "request_id": "uuid",
     "execution_generation": 1,
     "choice": "once|session|always|deny"
   }
   ```
4. `resolve_gateway_approval(session_key, choice, request_id)` processa
5. Tool executa ou é negada

**Choices (de `_approval_event_choices`):**
- `smart_denied=true` ou `allow_session=false` → `["once", "deny"]`
- `allow_permanent=true` → `["once", "session", "always", "deny"]`
- Default → `["once", "session", "deny"]`

**Modos (`approvals.mode`):**
- `manual` — Sempre pede (padrão APK)
- `smart` — Heurística auto-aprova "seguros"
- `off` — Nunca pede (PERIGOSO)

**YOLO mode (session-scoped):**
- `enable_session_yolo(session_key)` / `disable_session_yolo(session_key)`
- `is_session_yolo_enabled(session_key)` verifica
- Persistido em `approvals.db` (por session_key)

---

## Browser Launcher (tools/browser_launcher.py)

- **Requer Chromium + Playwright** — **NÃO FUNCIONA** no rootfs Android/proot
- `check_fn` / `requires` não encontrados no código atual — validação via `post_setup="chromium"` no toolset
- Probe `_probe_docker_backend` verifica `docker version` — falha sem Docker daemon
- `lightpanda` (browser engine local, sem Chromium) — **pode funcionar** (binário Zig, text-only, no screenshots)

---

## Tabela resumo para APK (default no config.yaml overlay)

```yaml
# rootfs/overlay/root/.hermes/config.yaml
agent:
  disabled_toolsets:
    - browser           # Chromium indisponível
    - computer_use      # cua-driver indisponível
    - stt               # faster-whisper sem wheels ARM64
    - image_gen         # precisa API key/fal
    - video_gen         # precisa API key/fal
    - x_search          # precisa xAI credencial
terminal:
  backend: local        # único que funciona no proot
approvals:
  mode: manual          # seguro por padrão
```