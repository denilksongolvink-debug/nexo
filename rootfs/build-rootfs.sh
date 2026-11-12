#!/bin/bash
# rootfs/build-rootfs.sh
# Constrói o rootfs Debian arm64 com Hermes e exporta como .tar.zst

set -euo pipefail

# Carrega versões
source "$(dirname "$0")/versions.env"

# Diretórios
ROOTFS_DIR="$(dirname "$0")"
OUT_DIR="${ROOTFS_DIR}/out"
TIMESTAMP=$(date -u +%Y%m%d-%H%M%S)
IMAGE_TAG="nexo-rootfs:${HERMES_REF}-${TIMESTAMP}"

mkdir -p "${OUT_DIR}"

echo "=== Building rootfs ==="
echo "HERMES_REF: ${HERMES_REF}"
echo "PYTHON_VERSION: ${PYTHON_VERSION}"
echo "EXTRAS_MINIMOS: web,mcp,acp,cron,uvloop"
echo "Output: ${OUT_DIR}/"

# Build multi-platform com buildx (usa qemu para arm64)
docker buildx build \
    --platform linux/arm64 \
    --load \
    -t "${IMAGE_TAG}" \
    -f "${ROOTFS_DIR}/Dockerfile" \
    "${ROOTFS_DIR}"

echo "=== Exporting rootfs ==="
# Cria container temporário para exportar filesystem
CID=$(docker create "${IMAGE_TAG}")

# Exporta como tar.zst (compressão zstd nível 19, multi-thread)
docker export "${CID}" | zstd -19 -T0 > "${OUT_DIR}/rootfs-arm64.tar.zst"

# Remove container temporário
docker rm "${CID}" >/dev/null

# Calcula SHA256
SHA256=$(sha256sum "${OUT_DIR}/rootfs-arm64.tar.zst" | cut -d' ' -f1)
echo "${SHA256}" > "${OUT_DIR}/rootfs-arm64.tar.zst.sha256"

# Tamanho
SIZE=$(du -h "${OUT_DIR}/rootfs-arm64.tar.zst" | cut -f1)

echo "=== Build complete ==="
echo "File: ${OUT_DIR}/rootfs-arm64.tar.zst"
echo "SHA256: ${SHA256}"
echo "Size: ${SIZE}"

# Verifica orçamento (≤ 300 MB comprimido)
SIZE_BYTES=$(stat -c%s "${OUT_DIR}/rootfs-arm64.tar.zst")
MAX_BYTES=$((300 * 1024 * 1024))
if [ "${SIZE_BYTES}" -gt "${MAX_BYTES}" ]; then
    echo "WARNING: Rootfs excede orçamento de 300 MB (${SIZE_BYTES} bytes)"
    echo "Considere podar: __pycache__, testes, docs, /var/cache/apt, /usr/share/doc"
    exit 1
else
    echo "OK: Rootfs dentro do orçamento (${SIZE_BYTES} bytes ≤ ${MAX_BYTES} bytes)"
fi

# Smoke test rápido
echo "=== Smoke test ==="
docker run --rm --platform linux/arm64 "${IMAGE_TAG}" \
    /opt/hermes/venv/bin/python -c "import hermes_cli; import fastapi; import uvicorn; import mcp; print('OK: imports funcionam')"

echo "=== All done ==="