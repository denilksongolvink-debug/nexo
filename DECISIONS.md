# DECISIONS.md

## D1 — runtime
escolha: proot + Debian glibc (padrão) vs Termux-bionic (fallback, Apêndice F)
motivo: wheels manylinux2014 aarch64 funcionam nativamente; Python 3.11 no Debian bookworm; proot roda no Android sem root
gatilho para reverter: se proot falhar no nativeLibraryDir (T4.10/Gate G4) → Apêndice F

## D2 — processos
escolha: 2 processos (hermes gateway run + hermes dashboard --no-open --host 127.0.0.1 --port 9119)
motivo: hermes serve é backend headless do desktop app (JSON-RPC/WS porta 9119), NÃO une gateway+dashboard. Gateway (porta 8642) roda plataformas+cron+API server; dashboard (porta 9119) roda REST admin+SPA. São independentes.
gatilho para reverter: se futura versão unificar em `serve` (improvável)

## D3 — transporte
escolha: edge (nexo_edge.py) — proxy reverso localhost com auth injetada
motivo: Dashboard bloqueia CORS/Origin para origens não-loopback; WebView tem origem diferente. Edge serve Nexo estático + proxy /hx/api/* e /hx/dash/* com headers injetados.
gatilho para reverter: se WebView suportar shouldInterceptRequest com body POST (não suporta)

## D4 — segurança do dashboard
escolha: token forte via env (32 bytes urlsafe, novo a cada boot) + dashboard on-demand (sobe só quando tela Configurações aberta)
motivo: Q2 confirmado — token vaza no HTML em loopback (window.__HERMES_SESSION_TOKEN__). No Android, qualquer app lê 127.0.0.1:9119. Duas mitigações: (1) token forte gerado no runtime.env, não hardcoded; (2) dashboard NÃO roda sempre — BackendService inicia/para sob demanda.
gatilho para reverter: se dashboard for necessário para WebSocket TUI (/api/pty) core → reavaliar com plugin Hermes ou proxy server-side

## D5 — agendamentos
escolha: API server (/api/jobs) — mesma auth do chat (Bearer API_SERVER_KEY)
motivo: Dashboard REST (/api/cron/jobs) exige token de sessão separado; API server já expõe CRUD de jobs com mesma autenticação do chat. Unifica permissões.
gatilho para reverter: se dashboard tiver funcionalidades de cron não expostas no API server