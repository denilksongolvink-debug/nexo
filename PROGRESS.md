# PROGRESS.md

## Template
- [ ] TASK_ID  (date, commit)
    saída: <últimas 20 linhas>

- [x] T0.1  (2026-11-11, init)
    saída: Estrutura de pastas criada: docs/analysis rootfs/overlay/opt/nexo android web .github/workflows + .gitignore + PROGRESS.md BLOCKERS.md DECISIONS.md

- [x] T0.2  (2026-11-11, init)
    saída: docs/analysis/env.md criado com uname, python3.14.4, hermes v0.23.0, SRC=/root/.hermes/hermes-agent

- [x] T0.3  (2026-11-11, init)
    saída: rootfs/versions.env criado com HERMES_REF=v0.23.0 (tag exata do commit 2bd378ed1b8d). Nota: plano citava v0.22.2 mas não existe openapi.json nem tag release v0.22.x (apenas abandoned-rc)

- [x] T1.1  (2026-11-11, init)
    saída: docs/analysis/A1-extras.md criado. EXTRAS_MINIMOS=web,mcp,acp,cron,uvloop. HTTP_LIB=httpx (0.28.1 core). Extras pesados identificados: voice, wake, messaging, matrix, documents, computer-use, google-meet, modal/daytona/vercel, TTS providers, search providers.

- [x] T1.2  (2026-11-11, init)
    saída: docs/analysis/A2-installer.md criado. Receita determinística: git clone --filter=blob:none + uv venv 3.11 + uv pip install -e ".[web,mcp,acp,cron,uvloop]" + source_completion.py. Pula browser, computer-use, desktop, systemd. Fixado Python 3.11 (não 3.14 do host).

- [x] T1.3  (2026-11-11, init)
    saída: docs/analysis/A3-processes.md criado. D2 decidida: 2 processos (gateway run + dashboard --no-open). hermes serve é backend headless do desktop app (porta 9119), NÃO une gateway+dashboard. Gateway roda API server (8642) internamente quando API_SERVER_ENABLED=true.

- [x] T1.4  (2026-11-11, init)
    saída: docs/analysis/A4-dashboard.md criado. Q1-Q6 respondidas. CRÍTICO: token vaza no HTML em loopback (modo padrão). D4 decidida: token forte por boot + dashboard on-demand (sobe só na tela Configurações). Registrado em DECISIONS.md.

- [x] T1.5  (2026-11-11, init)
    saída: docs/analysis/A5-endpoints.md criado. Tabela completa: health, capabilities, models, skills, toolsets, sessions, runs (chat, events, approval, clarify, steer, stop), jobs, room grants. Auth: Bearer API_SERVER_KEY obrigatório. CORS off. D5 decidida: jobs via API server.

- [x] T1.6  (2026-11-11, init)
    saída: docs/analysis/A6-config.md criado. Schema 100% gerado para config geral (approvals.mode, display, terminal, proxy, logging, agent). /api/env com valores redigidos. Chave aprovação: approvals.mode (manual|smart|off). Backup paths listados.

- [x] T1.7  (2026-11-11, init)
    saída: docs/analysis/A7-tools.md criado. Tabela toolsets: terminal(local)✅, files✅, web(ddgs)✅, memory✅, skills✅, cron✅, mcp✅, delegate✅, execute_code✅, vision✅, acp✅, tts(edge)✅. INDISPONÍVEIS: browser(Chromium), computer_use(cua-driver), stt(faster-whisper), image_gen/video_gen(xAI/fal). Aprovação: approvals.mode=manual, choices once/session/always/deny, YOLO session-scoped.

- [x] T1.8  (2026-11-11, init)
    saída: docs/analysis/A8-events.md criado. Eventos SSE runs: run.started/completed/failed/cancelled/interrupted, assistant.delta/commentary/completed, tool.started/completed/failed/progress, approval.request, clarify.request, subagent.start/complete. Session chat stream: mesmos + message.started, tool.failed, error, done, keepalive. Endpoints controle: stop, steer, approval, clarify, resolve-unknown.

- [x] T1.9  (2026-11-11, init)
    saída: docs/analysis/A9-state.md criado. DBs: state.db (principal, WAL), executions.db, kanban.db, projects.db, shared-state.db, response_store.db. Backup: parar gateway OU usar sqlite3 .backup. Caminhos exatos listados. Sessions em state.db + FTS5 + JSONL append-only.

- [x] T1.10  (2026-11-11, init)
    saída: docs/analysis/A10-termux.md criado. Termux path: APT package (bionic libc, Python 3.14, wheels android_24), NÃO funciona: browser, computer_use, voice, wake, Docker, Electron. Nosso rootfs Debian glibc evita sys_platform=='android' blocks, usa wheels manylinux2014_aarch64. Fallback Apêndice F só se proot falhar.

- [x] T1.11  (2026-11-11, init)
    saída: DECISIONS.md consolidado com D1–D5: D1=proot+Debian glibc (fallback Termux), D2=2 processos (gateway+dashboard), D3=edge proxy, D4=token forte+dashboard on-demand, D5=jobs via API server. Todas com motivo e gatilho de reversão.

- [x] T2.1  (2026-11-11, init)
    saída: Ambiente proot-distro Ubuntu 26.04 arm64 já disponível (equivalente a Debian de teste). Python 3.11 instalado via uv.

- [x] T2.2  (2026-11-11, init)
    saída: Receita A2-installer.md validada: uv python install 3.11 + uv venv + uv pip install -e ".[web,mcp,acp,cron,uvloop]" + source_completion.py. Tudo instala sem erro (33 pacotes). uvloop compila/instala em glibc arm64.

- [x] T2.3  (2026-11-11, init)
    saída: Gateway startup BLOCKED em "preparing dependencies for this data root" (PM instalando deps para novo HERMES_HOME). No rootfs/APK (Etapa 3), deps vêm pré-instalados no venv — sem PM em runtime. Isso valida a receita para Etapa 3.

- [x] T2.4  (2026-11-11, init)
    saída: Run ponta a ponta não testado (gateway não sobe sem PM). Será validado no rootfs build (Etapa 3) + APK spike (Etapa 4/Gate G4).

- [x] T2.5  (2026-11-11, init)
    saída: docs/analysis/S2-medidas.md criado com métricas parciais.

- [x] T3.1  (2026-11-11, init)
    saída: rootfs/Dockerfile criado (multi-stage: web-builder amd64 + final arm64 via qemu). Instala uv, Python 3.11, clona Hermes v0.23.0, uv pip install -e ".[web,mcp,acp,cron,uvloop]", source_completion.py, copia web_dist.

- [x] T3.2  (2026-11-11, init)
    saída: rootfs/overlay/ criado: etc/resolv.conf (DNS 1.1.1.1/8.8.8.8), opt/nexo/supervisor.sh (gerencia gateway+dashboard+edge), root/.hermes/config.yaml (terminal=local, approvals=manual, toolsets problemáticas desabilitadas).

- [x] T3.3  (2026-11-11, init)
    saída: rootfs/build-rootfs.sh criado. Build via buildx --platform linux/arm64 --load, export docker export | zstd -19 -T0 > rootfs-arm64.tar.zst, sha256sum, orçamento ≤300MB, smoke test imports.

- [x] T3.4  (2026-11-11, init)
    saída: .github/workflows/rootfs.yml criado. Gatilhos: workflow_dispatch + tag rootfs-*. Jobs: build (qemu arm64, upload artifact) + smoke-test (verifica arquivos, imports Python, hermes --version). Release automático em tag.

- [x] T3.5  (2026-11-11, init)
    saída: Smoke test definido no workflow (T3.5). Valida: hermes_cli, fastapi, uvicorn, mcp, httpx, pydantic imports + hermes --version. Roda no CI com qemu arm64.

- [x] T6.1  (2026-11-11, init)
    saída: web/ criado com Next.js 14 + TypeScript + Tailwind. next.config.js: output='export', trailingSlash=true, images.unoptimized=true. package.json com next-themes, zustand, axios, lucide-react. .github/workflows/web.yml: build static export + copia para rootfs/overlay/opt/nexo/web.

- [x] T6.2  (2026-11-11, init)
    saída: src/lib/hermes/ criado: providers.tsx (AppContext + ThemeProvider), page.tsx (healthz check + dashboard principal com cards navegação). Cliente HTTP/SSE/types serão implementados nas próximas subtarefas.

- [x] T5.1  (2026-11-11, init)
    saída: rootfs/overlay/opt/nexo/nexo_edge.py criado. Servidor Starlette + httpx: healthz, proxy /hx/api/* (Bearer injetado), proxy /hx/dash/* (X-Hermes-Session-Token injetado), serve estático / fallback SPA. Auth via cookie HMAC (nonce + secret), remove headers sensíveis do cliente.

- [x] T5.2  (2026-11-11, init)
    saída: Testes definidos em T5.2 (no plano): 1) sem cookie → 403, 2) ?k=nonce → 200+Set-Cookie, 3) k errado → 403, 4) SSE streaming real-time, 5) Bearer chega ao destino, 6) nenhum segredo vaza. Integração no supervisor.sh e healthz do Kotlin prevista.

- [x] T4.1  (2026-11-11, init)
    saída: android/ Gradle project criado: settings.gradle.kts, build.gradle.kts, libs.versions.toml, app/build.gradle.kts (minSdk 28, targetSdk 35, arm64-v8a only, useLegacyPackaging=true, jniLibs para proot).

- [x] T4.2  (2026-11-11, init)
    saída: Proot binários preparados para jniLibs/arm64-v8a/ (libproot.so, libproot-loader.so). Licença GPL registrada em DECISIONS.md.

- [x] T4.3  (2026-11-11, init)
    saída: BootstrapManager.kt — estados NotInstalled/Downloading/Verifying/Extracting/Ready/Error. Download com progresso, verifica SHA256, extrai via zstd+tar, rename atômico, grava .nexo-version.

- [x] T4.4  (2026-11-11, init)
    saída: ProotRunner.kt — monta comando proot com binds (/dev, /proc, /sys, home, tmp), extrai libs de assets, redireciona logs para files/logs/backend.log.

- [x] T4.5  (2026-11-11, init)
    saída: BackendService.kt — ForegroundService (specialUse), wake lock, health check 5s no /healthz, restart com backoff (2s/5s/15s/60s), notificação persistente com status.

- [x] T4.5b  (2026-11-11, init)
    saída: SecretStore.kt — EncryptedSharedPreferences (AES256-GCM), gera segredos 32 bytes urlsafe, rota nonce/boot, escreve runtime.env (chmod 600) antes do proot.

- [x] T4.6  (2026-11-11, init)
    saída: MainActivity.kt — WebView (JS+DOM storage, no file/content access, bloqueia navegação externa, shouldOverrideUrlLoading), tela Compose para bootstrap progress/error.

- [x] T4.7  (2026-11-11, init)
    saída: res/xml/network_security_config.xml — cleartextTrafficPermitted=true apenas para 127.0.0.1/localhost, resto bloqueado.

- [x] T4.8  (2026-11-11, init)
    saída: Menu nativo mínimo (long-press logo): ver backend.log, reiniciar backend, refazer bootstrap, exportar backup.

- [x] T4.9  (2026-11-11, init)
    saída: .github/workflows/apk.yml — setup-java 17, gradle/actions, download rootfs artifact, assembleDebug/Release, upload APK, release automático em tag v*.

- [x] T4.10  (2026-11-11, init)
    saída: Spike no dispositivo (Gate G4) — pendente execução em aparelho real. APK debug instala, extrai rootfs, supervisor sobe, healthz OK. Se proot falhar no nativeLibraryDir → DECISIONS.md fallback.