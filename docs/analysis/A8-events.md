# T1.8 — Eventos de Run (A8-events.md)

## Eventos SSE de `GET /v1/runs/{run_id}/events` (API Server Runs)

Todos os eventos têm formato base:
```json
{
  "event": "event_type",
  "run_id": "run_xxx",
  "timestamp": 1234567890.123,
  ...campos específicos...
}
```

### Eventos de ciclo de vida da run
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `run.started` | Run iniciada | `user_message`, `runtime` |
| `run.completed` | Run finalizada com sucesso | `session_id`, `message_id`, `completed: true`, `partial: false`, `interrupted: false`, `messages[]`, `usage`, `runtime` |
| `run.failed` | Run falhou | `completed: false`, `partial: false`, `interrupted: false`, `error`, `turn_exit_reason?` |
| `run.cancelled` | Run cancelada (stop/interrupt) | `completed: false`, `partial: false`, `interrupted: true` |
| `run.interrupted` | Run interrompida por shutdown do gateway | `error: "Gateway shutdown interrupted the run."` |
| `done` | Stream encerrado (sempre por último) | `{}` |

### Eventos de mensagem (assistant)
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `message.started` | Início da resposta do assistant | `message: {id, role: "assistant"}` |
| `assistant.delta` | Streaming de texto | `message_id`, `delta` (string chunk) |
| `assistant.commentary` | Comentário intermediário (ex: Codex phase="commentary") | `message_id`, `text`, `already_streamed` |
| `assistant.completed` | Resposta final completa | `session_id`, `message_id`, `content`, `completed`, `partial`, `interrupted`, `turn_exit_reason?`, `pending_steer?`, `runtime` |

### Eventos de tools
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `tool.started` | Tool iniciada | `tool` (nome), `preview` (args resumidos), `tool_call_id` |
| `tool.completed` | Tool finalizada com sucesso | `tool`, `duration` (segundos, 3 casas), `error: false`, `tool_call_id` |
| `tool.failed` | Tool falhou | `tool`, `duration`, `error: true`, `tool_call_id` |
| `tool.progress` | Progresso de reasoning/thinking | `message_id`, `tool_name: "_thinking"`, `delta` |
| `reasoning.available` | Reasoning disponível (legado) | `text` |

### Eventos de aprovação
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `approval.request` | Pedido de aprovação de comando perigoso | `request_id`, `execution_generation`, `tool`, `command` (redacted), `description` (redacted), `choices: ["once","session","always","deny"]` ou `["once","deny"]`, `smart_denied`, `allow_session`, `allow_permanent`, `session_key` |

**Resposta do cliente:** `POST /v1/runs/{run_id}/approval`
```json
{
  "request_id": "uuid",
  "execution_generation": 1,
  "choice": "once|session|always|deny"
}
```

### Eventos de clarify
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `clarify.request` | Pedido de esclarecimento (ambiguidade) | Similar a approval.request mas para clarify |

**Resposta:** `POST /v1/runs/{run_id}/clarify` com `{"input": "..."}`

### Eventos de steer (orientação mid-run)
- **Cliente envia:** `POST /v1/runs/{run_id}/steer` com `{"input": "..."}` ou `{"message": "..."}` ou `{"text": "..."}`
- **Evento SSE:** Não há evento SSE específico de "steer recebido" — o steer é injetado no agente e reflete nos deltas subsequentes
- **Status:** `pending_steer` carrega o texto de steer não entregue nos eventos terminais

### Eventos de unknown (estado desconhecido)
- **Evento:** `unknown` (emitido quando agente entra em estado desconhecido)
- **Resposta:** `POST /v1/runs/{run_id}/resolve-unknown` com `{"resolution": "..."}`
- **Permissão:** Requer permissão `stop` (mesma de `/stop`)

### Eventos de subagent
| Evento | Descrição | Campos (subset de `_SUBAGENT_EVENT_KEYS`) |
|--------|-----------|------------------------------------------|
| `subagent.start` | Subagent iniciado | `goal`, `task_count`, `task_index`, `subagent_id`, `child_session_id`, `delegation_id`, `parent_id`, `depth`, `model` |
| `subagent.complete` | Subagent finalizado | `status`, `summary`, `duration_seconds`, `input_tokens`, `output_tokens`, `reasoning_tokens`, `api_calls`, `cost_usd`, `files_read`, `files_written`, `output_tail` |

**Campos de texto redigidos:** `goal`, `summary`, `output_tail` (via `_SUBAGENT_TEXT_KEYS`)

---

## Eventos SSE de `POST /api/sessions/{session_id}/chat/stream` (Session Chat Stream)

Mesma estrutura base, mas eventos encapsulados com `message_id`:

### Eventos específicos
| Evento | Descrição | Campos |
|--------|-----------|--------|
| `run.started` | Run iniciada | `user_message`, `runtime` |
| `message.started` | Início resposta | `message: {id, role: "assistant"}` |
| `assistant.delta` | Streaming texto | `message_id`, `delta` |
| `assistant.commentary` | Comentário intermediário | `message_id`, `text`, `already_streamed` |
| `tool.started` | Tool iniciada | `message_id`, `tool_name`, `preview`, `args` |
| `tool.completed` | Tool sucesso | `message_id`, `tool_name`, `preview`, `args` |
| `tool.failed` | Tool erro | `message_id`, `tool_name`, `preview`, `args` |
| `tool.progress` | Reasoning/thinking | `message_id`, `tool_name: "_thinking"`, `delta` |
| `assistant.completed` | Resposta final | `session_id`, `message_id`, `content`, `completed`, `partial`, `interrupted`, `turn_exit_reason?`, `pending_steer?`, `runtime` |
| `run.completed` / `run.failed` / `run.cancelled` / `run.interrupted` | Terminal | `session_id`, `message_id`, `messages[]`, `usage`, `runtime`, flags |
| `error` | Erro no stream | `message` |
| `done` | Stream encerrado | `{}` |

**Keepalive:** `: keepalive\n\n` a cada `CHAT_COMPLETIONS_SSE_KEEPALIVE_SECONDS` (default 15s) se sem eventos.

---

## Controle de run (POST endpoints)

| Endpoint | Método | Body | Descrição |
|----------|--------|------|-----------|
| `/v1/runs/{run_id}/stop` | POST | `{}` | Para run (cooperative stop → interrupt) |
| `/v1/runs/{run_id}/steer` | POST | `{"input": "..."}` | Orienta run em andamento |
| `/v1/runs/{run_id}/approval` | POST | `{"request_id": "...", "execution_generation": 1, "choice": "once|session|always|deny"}` | Responde aprovação |
| `/v1/runs/{run_id}/clarify` | POST | `{"input": "..."}` | Responde clarify |
| `/v1/runs/{run_id}/resolve-unknown` | POST | `{"resolution": "..."}` | Resolve estado unknown |

---

## Exemplo real capturado (simulado baseado no código)

### `tool.started`
```json
{"event":"tool.started","run_id":"run_abc123","timestamp":1700000000.123,"tool":"terminal","preview":"echo ok","tool_call_id":"call_123"}
```

### `tool.completed`
```json
{"event":"tool.completed","run_id":"run_abc123","timestamp":1700000001.456,"tool":"terminal","duration":1.333,"error":false,"tool_call_id":"call_123"}
```

### `approval.request`
```json
{"event":"approval.request","run_id":"run_abc123","timestamp":1700000000.500,"request_id":"req_456","execution_generation":1,"tool":"terminal","command":"[REDACTED]","description":"Execute shell command","choices":["once","session","deny"],"smart_denied":false,"allow_session":true,"allow_permanent":false,"session_key":"sess_789"}
```

### `assistant.delta`
```json
{"event":"assistant.delta","run_id":"run_abc123","timestamp":1700000002.000,"message_id":"msg_xyz","delta":"Aqui está o resultado: "}
```

### `run.completed`
```json
{"event":"run.completed","run_id":"run_abc123","timestamp":1700000005.000,"session_id":"sess_789","message_id":"msg_xyz","completed":true,"partial":false,"interrupted":false,"messages":[...],"usage":{"input_tokens":100,"output_tokens":50},"runtime":{"model":"anthropic/claude-sonnet-4.6","provider":"anthropic"}}
```