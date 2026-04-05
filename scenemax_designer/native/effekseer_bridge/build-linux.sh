#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build-linux"
EFFEKSEER_ROOT="${EFFEKSEER_ROOT:-${ROOT_DIR}/third_party/Effekseer}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a JDK before building scenemax_effekseer_jni." >&2
  exit 1
fi

cmake -S "${SCRIPT_DIR}" \
  -B "${BUILD_DIR}" \
  -G "Unix Makefiles" \
  -DEFFEKSEER_ROOT="${EFFEKSEER_ROOT}"

cmake --build "${BUILD_DIR}" --config Release

echo
echo "Expected packaged output:"
echo "  ${ROOT_DIR}/scenemax_effekseer_runtime/assets/native/linux-x86_64/libscenemax_effekseer_jni.so"
