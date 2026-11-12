# T1.6 — Config, Env e Schema (A6-config.md)

## Formato do Schema (`GET /api/config/schema`)

Retorna: `{"fields": {...}, "category_order": [...]}`

Cada field em `fields` tem:
```json
{
  "type": "boolean|number|string|list|object|select",
  "description": "Human-readable description",
  "category": "general|agent|display|security|discord|...",
  "options": ["opt1", "opt2", ...],  // só para type="select"
  "searchable": true,                // opcional, para selects grandes
  "clearable": true                  // opcional, para selects
}
```

**Exemplos de campos por tipo:**

| Campo | Tipo | Categoria | Options | Descrição |
|-------|------|-----------|---------|-----------|
| `approvals.mode` | select | security | `["manual", "smart", "off"]` | Modo de aprovação de comandos perigosos |
| `model` | string | general | — | Modelo padrão (ex: `anthropic/claude-sonnet-4.6`) |
| `model_context_length` | number | general | — | Context window override (0 = auto) |
| `timezone` | select | general | IANA timezones | Timezone (blank = system) |
| `memory.provider` | select | agent | `["", "chromadb", "pinecone", ...]` | Memory provider plugin |
| `terminal.backend` | select | agent | `["local", "docker", "ssh", "modal", "daytona", "vercel_sandbox"]` | Terminal backend |
| `proxy.enabled` | boolean | security | — | Docker egress credential firewall |
| `tts.provider` | select | display | `["edge", "elevenlabs", "openai", ...]` | TTS provider |
| `stt.provider` | select | display | `["local", "groq", "openai", ...]` | STT provider |
| `display.skin` | select | display | `["default", "ares", "mono", "slate"]` | CLI theme |
| `dashboard.theme` | select | display | `["default", "midnight", "ember", ...]` | Web dashboard theme |
| `logging.level` | select | general | `["DEBUG", "INFO", "WARNING", "ERROR"]` | Log level |
| `agent.service_tier` | select | agent | `["", "normal", "fast", "auto", "cold"]` | Fast mode |

**`category_order`** define a ordem das abas na UI (ex: `["general", "agent", "display", "security", "discord"]`). Categorias pequenas são fundidas via `_CATEGORY_MERGE` (ex: `privacy` → `security`, `context` → `agent`).

**Geração dinâmica:** `_schema_with_dynamic_provider_options()` mescla options de providers descobertos em runtime (tts, stt, memory, terminal backend plugins) — reflete plugins instalados após boot.

---

## Chave de aprovação (`approvals.mode`)

**Chave exata:** `approvals.mode` (category: `security`)

**Valores:**
- `"manual"` — Pede aprovação para cada comando perigoso (padrão recomendado para APK)
- `"smart"` — Aprova automaticamente comandos "seguros" (heurística)
- `"off"` — Nunca pede aprovação (PERIGOSO — aviso explícito na UI)

---

## Endpoint `/api/env` — Variáveis de ambiente

**GET `/api/env`** retorna objeto com chaves = nome da env var, valores:
```json
{
  "OPENAI_API_KEY": {
    "is_set": true,
    "redacted_value": "sk-****************************",
    "description": "OpenAI API key",
    "url": "https://platform.openai.com/api-keys",
    "category": "provider",
    "is_password": true,
    "tools": ["chat", "embeddings"],
    "advanced": false,
    "channel_managed": false,
    "provider": "openai",
    "provider_label": "OpenAI",
    "provider_profiles": [],
    "provider_primary": true,
    "custom": false
  }
}
```

- **Valores SEMPRE redigidos** (`redacted_credential_preview`): mostra prefixo + asteriscos
- `is_set`: boolean se existe valor
- `channel_managed`: true se gerenciado pela tela Channels (não editar aqui)
- `custom`: true se chave arbitrária do `.env` não reconhecida
- `provider_primary`: true para o primeiro profile do provider (campo principal no UI)

**PUT `/api/env`** — salva credencial (reconcilia `.env` + `config.yaml` mirrors)
- Rejeita valor redigido (400: "Credential value appears to be a redacted preview")
- Aceita `provider_setup: true` para métricas

**DELETE `/api/env`** — remove credencial

**POST `/api/env/reveal`** — revela valor real (para copy/paste)

---

## A tela de Configurações pode ser 100% gerada pelo schema?

**SIM.** O schema cobre:
1. Todos os campos de `config.yaml` (via `DEFAULT_CONFIG` walk)
2. Options dinâmicas de providers (TTS, STT, memory, terminal backend) descobertos em runtime
3. Categorias e ordem de abas (`category_order`)
4. Validação de tipos (boolean, number, string, select)
5. Descrições human-readable

**Limitações:**
- Campos "virtual" como `model_context_length` são injetados manualmente no schema
- Provider options descobertos via plugin scan (pode ser lento — roda em worker thread)
- `custom_providers` e `providers` (blocos aninhados complexos) não estão no schema flat — gerenciados em telas dedicadas (Model picker, Provider setup)
- `agent.personalities`, `skills` — UI dedicada

**Conclusão:** Para configuração geral (approvals, display, terminal, proxy, logging, agent, etc.) — **100% schema-driven**. Para providers/models/skills — telas dedicadas que usam endpoints específicos (`/api/model/options`, `/v1/skills`, `/api/mcp/*`).

---

## Backup paths (para T7.4 / A9-state.md)

| Path | Descrição |
|------|-----------|
| `$HERMES_HOME/config.yaml` | Config principal |
| `$HERMES_HOME/.env` | Secrets (chaves API) — **chmod 600** |
| `$HERMES_HOME/skills/` | Skills instaladas |
| `$HERMES_HOME/memory/` | Memory providers data |
| `$HERMES_HOME/state.db` | SQLite (sessions, runs, jobs, admissions) — WAL mode |
| `$HERMES_HOME/state.db-shm` / `state.db-wal` | WAL files (precisam no backup) |
| `$HERMES_HOME/cron/` | Cron job definitions |
| `$HERMES_HOME/logs/` | Logs (opcional) |