# T1.10 — Caminho Termux Oficial (A10-termux.md)

## Referência: `website/docs/getting-started/termux.md`

### O que é "tested path" (caminho testado/suportado)

- **Pacote APT assinado** no repositório `https://hermes-assets.nousresearch.com/releases/termux/stable`
- Instala via `pkg install hermes-agent` no **Termux** (app oficial)
- **Prefixo fixo:** `/data/data/com.termux/files/usr` (hardcoded nos pacotes)
- **Arquitetura:** aarch64 (arm64-v8a) apenas
- **Python:** 3.14 com **bionic libc** (Termux interpreter pin)
- **Wheels:** Compilados no CI para `android_24_arm64_v8a` — **não compila no device**
- **Extras incluídos:** core + `acp` (não todos os extras desktop)
- **Comandos:** `hermes`, `hermes-agent`, `hermes-acp` (symlinks em `$PREFIX/bin`)

### O que NÃO funciona no Android (Termux)

| Feature | Motivo |
|---------|--------|
| **Browser tools** (Local Browser) | Precisa Chromium + Playwright; `pkg install chromium` pode funcionar mas pesado |
| **Computer Use** (cua-driver) | Binário nativo macOS/Win/Linux X11 — não existe build Android |
| **Voice/STT local** (faster-whisper) | `ctranslate2` + `onnxruntime` sem wheels Android arm64 |
| **Wake word** (pyopen-wakeword, pvporcupine) | Binários TFLite/nativos sem suporte Android |
| **Docker** | Daemon não disponível no Termux |
| **Electron/desktop app** | Não incluído no pacote APT |
| **nemo-relay exporter** | Toolchain não suporta target |
| **Phone-native Termux:API** | Microfone/clipboard adapters não incluídos |
| **Python gated `sys_platform != 'android'`** | Dependências com marker `sys_platform != 'android'` não instalam |

### Extras disponíveis no Termux (do `pyproject.toml`)

```toml
termux = [
  "python-telegram-bot[webhooks]==22.8",
  "hermes-agent[cron]",
  "hermes-agent[mcp]",
  "hermes-agent[acp]",
]

termux-all = [
  "hermes-agent[termux]",
  "hermes-agent[google]",
  "hermes-agent[sms]",
  "hermes-agent[web]",
  "hermes-agent[pty]",
]
```

### Constraints

- **Não existe `constraints-termux.txt` no repo v0.23.0** (era em versões antigas)
- O pin de versões vem do **APT package metadata** (compilado no CI)
- Wheels pré-compiladas para Android API 24 (bionic)

---

## Nosso Caso: Rootfs Debian glibc (proot) vs Termux bionic

| Aspecto | Termux (bionic) | Nosso Rootfs (Debian glibc) |
|---------|-----------------|----------------------------|
| **libc** | bionic (Android) | glibc (Debian bookworm) |
| **Python** | 3.14 (bionic pin) | 3.11 (Debian default) / 3.11 via uv |
| **Wheels** | `android_24_arm64` (custom CI) | `manylinux2014_aarch64` (PyPI standard) |
| **ctranslate2/onnxruntime** | ❌ Sem wheels | ⚠️ Tem wheels manylinux aarch64 (podem funcionar) |
| **Playwright/Chromium** | ❌ Pesado/box64 | ❌ Igual |
| **Docker** | ❌ Não | ❌ Não (no proot) |
| **Terminal backend `local`** | ✅ Funciona | ✅ Funciona (binários glibc nativos) |
| **`proot` necessário?** | Não (roda nativo) | **Sim** (para glibc no Android) |

### Problemas do Android que NÃO se aplicam ao nosso rootfs Debian glibc

| Problema Termux | Nosso caso |
|-----------------|------------|
| `sys_platform == 'android'` bloqueia deps | `sys_platform == 'linux'` — deps Linux liberadas |
| Wheels custom `android_24` necessárias | Wheels standard `manylinux2014_aarch64` funcionam |
| Python 3.14 bionic pin | Python 3.11 glibc (Debian) — versões estáveis |
| Prefixo hardcoded `/data/data/com.termux/files/usr` | `/opt/hermes/venv` + proot bind mounts — flexível |
| Gateway só roda em foreground (sem systemd) | Mesmo — supervisor.sh gerencia |
| Android mata processos background | Mesmo risco — mitigado por T7.2 |

### Problemas do Android que AINDA se aplicam

| Problema | Mitigação no APK |
|----------|------------------|
| **Android mata processos filhos** (phantom process killer) | T7.2: battery optimization exemption, `settings put global ...` |
| **proot não executa de `nativeLibraryDir`** (Android 10+) | T4.2: empacotar proot como `.so` + `useLegacyPackaging=true`; fallback T4.10 |
| **Sem Docker daemon** | Terminal backend `local` apenas; `docker`/`modal`/`daytona` desabilitados |
| **Sem systemd** | Supervisor.sh + BackendService (foreground service) |
| **Loopback compartilhado entre apps** | D4: token forte + dashboard on-demand |

---

## Decisão: Fallback Termux (Apêndice F)

**SÓ ativar se G2/G4 falharem** (proot não funciona no `nativeLibraryDir`):

1. Fork `termux-app` + `termux-packages` com **package name próprio** (prefixo `/data/data/<pkg>/files/usr` embutido nos pacotes)
2. Build no CI: `pkg install python nodejs uv` + `python -m venv` (não `uv`) + `ANDROID_API_LEVEL=$(getprop ro.build.version.sdk)` + `pip install -e '.[termux]' -c constraints-termux.txt` (se existir)
3. Custos: licença GPLv3 do Termux, build muito mais pesado, sem browser/voz
4. Registrar em `DECISIONS.md` com gatilho explícito