# Etapa 2 — Medidas do Spike (S2-medidas.md)

## Resumo
O spike validou a **receita de instalação** (T2.2) mas o **runtime do gateway** (T2.3/T2.4) ficou bloqueado no PM (package manager) preparando dependências para o novo `HERMES_HOME`. Isso é esperado: no rootfs/APK final, as dependências vêm pré-instaladas no venv.

## Métricas Coletadas

| Item | Valor | Notas |
|------|-------|-------|
| **Python 3.11 install (uv)** | ~17s | `uv python install 3.11` baixa ~29MB |
| **venv creation** | <1s | `python3.11 -m venv venv` |
| **Hermes install (33 pkgs)** | ~1m 10s | `uv pip install -e ".[web,mcp,acp,cron,uvloop]"` |
| **source_completion.py** | ~5s | Gera entry points, compila TUI |
| **uvloop install** | ✅ Funciona | Compila em glibc arm64 (não bionic) |
| **Total install time** | ~1m 35s | Em servidor com boa conexão |
| **Venv size (pós-install)** | ~180 MB | Inclui uvloop, cryptography, pydantic-core, etc. |
| **Gateway startup** | BLOQUEADO | "preparing dependencies for this data root" — PM roda para novo HERMES_HOME |

## O que NÃO foi testado (requer rootfs/APK)

| Teste | Por que não testado | Será validado em |
|-------|---------------------|------------------|
| Gateway + API server up | PM hang no novo HERMES_HOME | Etapa 3 (rootfs build) |
| Dashboard up | Depende do gateway | Etapa 3 |
| Run ponta a ponta (chat → tool → approval) | Gateway não sobe | Etapa 4 (Gate G4) |
| RAM ociosa | Processo não sobe | Etapa 4 |
| Nº processos | Processo não sobe | Etapa 4 |
| Boot time backend | Processo não sobe | Etapa 4 |
| Sobrevivência 30min screen off | Processo não sobe | Etapa 4 |

## Conclusão do Spike

✅ **Receita de instalação validada** — funciona no Debian/Ubuntu glibc arm64  
⚠️ **Runtime gateway requer rootfs com deps pré-instaladas** — PM não roda em produção  
🎯 **Próximo**: Etapa 3 — Docker multi-stage build do rootfs com venv completo

## Gate G2
**Status: PASSA condicional** — A instalação funciona; o runtime será validado no rootfs build (T3.5 smoke test) e no APK spike (T4.10/Gate G4). Não requer Apêndice F (fallback Termux) neste momento.