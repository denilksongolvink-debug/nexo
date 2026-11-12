# T1.9 — Estado e Sessões (A9-state.md)

## Localização dos Bancos de Dados

Todos os bancos ficam em `$HERMES_HOME` (default `~/.hermes`):

| Arquivo | Descrição | WAL? | Backup necessário? |
|---------|-----------|------|-------------------|
| `state.db` | **Principal** — sessions, runs, admissions, tool calls, FTS5 search | ✅ Sim (state.db-wal, state.db-shm) | **SIM** (incluir -wal, -shm) |
| `executions.db` | Cron job executions (em `$HERMES_HOME/cron/`) | Provavelmente | **SIM** |
| `kanban.db` | Kanban boards (multi-profile collab) | ✅ Sim | **SIM** |
| `projects.db` | Projects/workspaces (git roots, session-derived) | Provavelmente | **SIM** |
| `shared-state.db` | Shared state cross-profile | Provavelmente | **SIM** |
| `response_store.db` | OpenAI Responses API state (por profile) | Provavelmente | **SIM** |
| `sessions/<id>.jsonl` | Mensagens brutas por sessão (append-only) | Não | Opcional (pode regenerar do state.db) |

## SQLite WAL Mode

- **Sim, usa WAL** (Write-Ahead Logging) — `PRAGMA journal_mode=WAL`
- Permite **leitores concorrentes + 1 writer**
- Arquivos sidecar: `-wal` (write-ahead log), `-shm` (shared memory index)
- **Checkpoint passivo** a cada N writes (configurável)
- Pool de conexões read-only bounded para leituras

## Segurança de cópia com app rodando

**NÃO é seguro copiar `state.db` enquanto o gateway roda** — o writer pode estar no meio de transação, WAL pode ter frames não checkpointados.

**Estratégia de backup seguro:**
1. **Parar o gateway** (`hermes gateway stop`) antes do backup
2. Ou usar **SQLite backup API** (`.backup`):
   ```bash
   sqlite3 $HERMES_HOME/state.db ".backup /backup/state.db"
   ```
   - Funciona com app rodando (usa `sqlite3_backup` incremental)
   - Copia main db + WAL frames consistentemente
3. Para `executions.db`, `kanban.db`, etc. — mesma abordagem

## Lista exata de caminhos para backup (T7.4)

```
$HERMES_HOME/
├── config.yaml              # Config principal
├── .env                     # Secrets (chmod 600!) — **NUNCA commitar**
├── state.db                 # Principal (SIM)
├── state.db-wal             # WAL (SIM)
├── state.db-shm             # SHM (SIM)
├── executions.db            # Cron (em cron/) (SIM)
├── kanban.db                # Kanban (SIM)
├── kanban.db-wal            # (SIM)
├── kanban.db-shm            # (SIM)
├── projects.db              # Projects (SIM)
├── shared-state.db          # Shared state (SIM)
├── response_store.db        # Responses API (SIM)
├── cron/                    # Cron job definitions (SIM)
├── skills/                  # Skills instaladas (SIM)
├── memory/                  # Memory providers data (SIM)
├── logs/                    # Logs (opcional)
├── sessions/                # JSONL por sessão (opcional — regenerável)
├── pairing/                 # Pairing data (opcional)
├── hooks/                   # Hooks (opcional)
├── image_cache/             # Cache (não)
├── audio_cache/             # Cache (não)
└── cache/                   # Cache terminal, etc (não)
```

## Migração de config

- `hermes doctor --fix` roda migrações de schema
- `/api/ops/config-migrate` endpoint existe para migração via dashboard
- `config.yaml` versionado via `_config_version` (atual: 14+)

## Sessões

- Armazenadas em `state.db` (tabela `sessions`) + FTS5 para busca
- Mensagens completas em `state.db` + append-only em `sessions/<id>.jsonl`
- Origem (`source`): `api_server`, `cli`, `cron`, `gateway`, `webhook`, etc.
- Busca: `GET /api/sessions/search` (dashboard) ou FTS em `state.db`
- Fork: `POST /api/sessions/{id}/fork` cria nova sessão com histórico copiado