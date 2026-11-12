# T1.5 — API Server Endpoints (A5-endpoints.md)

## Tabela completa de rotas do API Server (porta 8642)

### Health & Capabilities
| Método | Caminho | Auth | Descrição | Usado pelo Nexo |
|--------|---------|------|-----------|-----------------|
| GET | `/health` | Não | Health check simples | Sim (healthz do edge) |
| GET | `/health/detailed` | Não | Health detalhado | Sim |
| GET | `/v1/health` | Não | Alias OpenAI-compat | Sim |
| GET | `/v1/capabilities` | Não | Feature flags + endpoints table | Sim (descobrir features) |
| GET | `/v1/models` | Bearer | Lista modelos disponíveis | Sim (model picker) |
| GET | `/api/model/options` | Bearer | Opções de modelo (providers) | Sim (onboarding provider) |

### Skills & Toolsets
| Método | Caminho | Auth | Descrição | Usado pelo Nexo |
|--------|---------|------|-----------|-----------------|
| GET | `/v1/skills` | Bearer | Lista skills instaladas | Sim (tela Skills) |
| GET | `/v1/toolsets` | Bearer | Lista toolsets disponíveis | Sim (tela Configurações) |

### Sessions (API Server — source="api_server")
| Método | Caminho | Auth | Descrição | Usado pelo Nexo |
|--------|---------|------|-----------|-----------------|
| GET | `/api/sessions` | Bearer | Lista sessões (filtro `source`, `limit`, `offset`) | Sim (lista sessões) |
| POST | `/api/sessions` | Bearer | Cria sessão | Sim (nova conversa) |
| GET | `/api/sessions/{session_id}` | Bearer | Detalhes da sessão | Sim (abrir sessão) |
| PATCH | `/api/sessions/{session_id}` | Bearer | Atualiza sessão (rename, etc) | Sim (renomear) |
| DELETE | `/api/sessions/{session_id}` | Bearer | Deleta sessão (409 se busy) | Sim (excluir) |
| GET | `/api/sessions/{session_id}/messages` | Bearer | Mensagens da sessão | Sim (histórico) |
| POST | `/api/sessions/{session_id}/fork` | Bearer | Fork da sessão | Sim (fork) |
| POST | `/api/sessions/{session_id}/chat` | Bearer | Chat não-streaming | Opcional |
| POST | `/api/sessions/{session_id}/chat/stream` | Bearer | Chat streaming (SSE) | **Sim (core do chat)** |
| POST | `/api/sessions/{session_id}/model` | Bearer | Lock model da sessão | Sim (trocar modelo) |

### Runs (OpenAI-compatible + Hermes extensions)
| Método | Caminho | Auth | Descrição | Usado pelo Nexo |
|--------|---------|------|-----------|-----------------|
| POST | `/v1/chat/completions` | Bearer | OpenAI chat completions | Não (usamos runs) |
| POST | `/v1/responses` | Bearer | OpenAI responses API | Não |
| GET | `/v1/responses/{response_id}` | Bearer | Get response | Não |
| DELETE | `/v1/responses/{response_id}` | Bearer | Delete response | Não |
| **POST** | **`/v1/runs`** | **Bearer** | **Cria run (input, session_id?, model?, tools?)** | **SIM (enviar mensagem)** |
| GET | `/v1/runs/{run_id}` | Bearer | Status da run | Sim (polling fallback) |
| **GET** | **`/v1/runs/{run_id}/events`** | **Bearer** | **SSE events (streaming)** | **SIM (core do chat)** |
| POST | `/v1/runs/{run_id}/approval` | Bearer | Responder aprovação (choices: once/session/always/deny) | **SIM (modal aprovação)** |
| POST | `/v1/runs/{run_id}/clarify` | Bearer | Responder clarify | **SIM (modal clarify)** |
| POST | `/v1/runs/{run_id}/steer` | Bearer | Orientar run em andamento | **SIM (botão steer)** |
| POST | `/v1/runs/{run_id}/resolve-unknown` | Bearer | Resolver unknown | **SIM (botão resolve-unknown)** |
| POST | `/v1/runs/{run_id}/stop` | Bearer | Parar run | **SIM (botão stop)** |

### Jobs / Cron (API Server)
| Método | Caminho | Auth | Descrição | Usado pelo Nexo |
|--------|---------|------|-----------|-----------------|
| GET | `/api/jobs` | Bearer | Lista jobs | **Sim (tela Agendamentos)** |
| POST | `/api/jobs` | Bearer | Cria job | **Sim** |
| GET | `/api/jobs/{job_id}` | Bearer | Detalhes job | **Sim** |
| PATCH | `/api/jobs/{job_id}` | Bearer | Atualiza job | **Sim** |
| DELETE | `/api/jobs/{job_id}` | Bearer | Deleta job | **Sim** |
| POST | `/api/jobs/{job_id}/pause` | Bearer | Pausa job | **Sim** |
| POST | `/api/jobs/{job_id}/resume` | Bearer | Retoma job | **Sim** |
| POST | `/api/jobs/{job_id}/run` | Bearer | Executa agora | **Sim** |

### Platform Events (ingress — auth diferente)
| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/platforms/{platform}/events` | Platform-specific | Callback de plataformas (Telegram, etc) |

### Cron Fire (NAS webhook)
| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/api/cron/fire` | NAS JWT | Webhook NAS → agent |

### Room Grants (Hosted rooms)
| Método | Caminho | Auth | Descrição |
|--------|---------|------|-----------|
| POST | `/v1/room-members/invitations` | Bearer | Convidar membro para room |
| GET | `/v1/room-members/capabilities` | Bearer | Capabilities do room |
| POST | `/v1/room-members/grants/refresh` | Bearer | Refresh grant |
| POST | `/v1/room-members/grants/revoke` | Bearer | Revogar grant |

---

## Autenticação

**Bearer token obrigatório:** `Authorization: Bearer <API_SERVER_KEY>`
- `API_SERVER_KEY` configurada via env var ou config.yaml
- **Sem API key → 401/403 em todas as rotas exceto `/health*` e `/v1/capabilities`**
- Header `X-Hermes-Session-Key` opcional para scoping de memória por conversa (requer API key)

---

## CORS

**CORS DESLIGADO por padrão.** O API server não configura CORS — só aceita requisições same-origin (loopback). O edge (nexo_edge.py) faz o proxy e injeta o Bearer, então o Nexo (mesma origem do edge) não precisa de CORS.

---

## Comparação com openapi.json (v0.22.2 referenciado no plano)

**Não existe openapi.json no repo v0.23.0.** As rotas acima são extraídas do código (`_http_route_table` + `_api_runs._http_routes` + `_room_grants._http_routes`). Diferenças prováveis vs v0.22.2:
- `/v1/responses` e `/v1/responses/{id}` — OpenAI Responses API (novo em v0.23+)
- `/v1/runs/{run_id}/resolve-unknown` — estado unknown (novo)
- `/api/cron/fire` — NAS webhook
- Room grants endpoints — hosted rooms

---

## DECISÃO D5 (registrada em DECISIONS.md)

```markdown
## D5 — agendamentos
escolha: API server (/api/jobs) — mesma auth do chat (Bearer API_SERVER_KEY)
motivo: Dashboard REST (/api/cron/jobs) exige token de sessão separado (X-Hermes-Session-Token) e dashboard roda on-demand (D4). API server já expõe CRUD completo de jobs com mesma autenticação do chat. Unifica permissões e evita depender do dashboard.
gatilho para reverter: se dashboard tiver funcionalidades de cron não expostas no API server (ex: UI de templates, validações complexas)
```