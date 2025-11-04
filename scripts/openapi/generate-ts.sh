#!/usr/bin/env bash
set -euo pipefail
SPEC="${1:-${FOUNDATION_DIR:-/var/foundation}/apis/openapi.yaml}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/product/src/main/resources/typescript-runtime/generated"

mkdir -p "$OUT_DIR"
echo "Generating TS client from: $SPEC → $OUT_DIR"

# Prefer local openapi-generator if present
if command -v openapi-generator >/dev/null 2>&1; then
  openapi-generator generate -i "$SPEC" -g typescript-axios -o "$OUT_DIR" --skip-validate-spec
else
  docker run --rm -v "$ROOT_DIR:/work" -w /work openapitools/openapi-generator-cli:v7.9.0 \
    generate -i "$SPEC" -g typescript-axios -o "$(realpath --relative-to="$ROOT_DIR" "$OUT_DIR")" --skip-validate-spec
fi

echo "Done."
