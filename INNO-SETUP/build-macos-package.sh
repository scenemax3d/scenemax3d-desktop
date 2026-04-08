#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PACKAGE_TYPE="${1:-app-image}"
APP_NAME="SceneMax3D"
APP_VERSION="$(grep -oE 'APPLICATION_VERSION[[:space:]]*=[[:space:]]*"[^"]+"' "${REPO_ROOT}/src/com/scenemax/desktop/Util.java" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')"
STAGE_DIR="${SCRIPT_DIR}/jpackage/macos"
INPUT_DIR="${STAGE_DIR}/input"
OUTPUT_DIR="${SCRIPT_DIR}/Output/macos"
MAIN_JAR_SOURCE="${REPO_ROOT}/build/libs/scenemax_desktop-1.0-SNAPSHOT-all.jar"
PROJECTOR_JAR_SOURCE="${REPO_ROOT}/out/artifacts/scenemax_win_projector.jar"

mkdir -p "${INPUT_DIR}/out/artifacts" "${INPUT_DIR}/data" "${INPUT_DIR}/export_targets" "${OUTPUT_DIR}"

"${REPO_ROOT}/gradlew" build :scenemax_win_projector:publishProjectorArtifact

cp "${MAIN_JAR_SOURCE}" "${INPUT_DIR}/scenemax_desktop.jar"
cp "${PROJECTOR_JAR_SOURCE}" "${INPUT_DIR}/out/artifacts/scenemax_win_projector.jar"
cp "${REPO_ROOT}/data/scenemax3d.db" "${INPUT_DIR}/data/scenemax3d.db"
cp -R "${REPO_ROOT}/resources-basic/resources" "${INPUT_DIR}/resources"
cp -R "${REPO_ROOT}/macro" "${INPUT_DIR}/macro"
cp "${REPO_ROOT}/export_targets/android_native.zip" "${INPUT_DIR}/export_targets/android_native.zip"

jpackage \
  --type "${PACKAGE_TYPE}" \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --dest "${OUTPUT_DIR}" \
  --input "${INPUT_DIR}" \
  --main-jar "scenemax_desktop.jar" \
  --main-class "com.scenemax.desktop.MainApp"

echo "macOS package ready under ${OUTPUT_DIR}"
