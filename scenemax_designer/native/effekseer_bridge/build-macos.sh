#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
EFFEKSEER_ROOT="${EFFEKSEER_ROOT:-${ROOT_DIR}/third_party/Effekseer}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a JDK before building scenemax_effekseer_jni." >&2
  exit 1
fi

ARCH="${1:-}"
if [[ -z "${ARCH}" ]]; then
  echo "Usage: ./build-macos.sh x86_64|arm64" >&2
  exit 1
fi

case "${ARCH}" in
  x86_64)
    PLATFORM_DIR="macos-x86_64"
    ;;
  arm64|aarch64)
    ARCH="arm64"
    PLATFORM_DIR="macos-aarch64"
    ;;
  *)
    echo "Unsupported macOS arch: ${ARCH}. Use x86_64 or arm64." >&2
    exit 1
    ;;
esac

BUILD_DIR="${SCRIPT_DIR}/build-macos-${ARCH}"

cmake -S "${SCRIPT_DIR}" \
  -B "${BUILD_DIR}" \
  -G "Unix Makefiles" \
  -DEFFEKSEER_ROOT="${EFFEKSEER_ROOT}" \
  -DCMAKE_OSX_ARCHITECTURES="${ARCH}"

cmake --build "${BUILD_DIR}" --config Release

echo
echo "Expected packaged output:"
echo "  ${ROOT_DIR}/scenemax_effekseer_runtime/assets/native/${PLATFORM_DIR}/libscenemax_effekseer_jni.dylib"
