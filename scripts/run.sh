#!/usr/bin/env bash
set -euo pipefail

# Run script for jfr-performance-mcp
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

JAR_FILE=$(find "${PROJECT_ROOT}/target" -maxdepth 1 -name "jfr-performance-mcp-*.jar" ! -name "original-*.jar" | head -n 1)

if [[ -z "${JAR_FILE}" || ! -f "${JAR_FILE}" ]]; then
    echo "Executable JAR not found in target/. Building project first..." >&2
    "${SCRIPT_DIR}/build.sh"
    JAR_FILE=$(find "${PROJECT_ROOT}/target" -maxdepth 1 -name "jfr-performance-mcp-*.jar" ! -name "original-*.jar" | head -n 1)
fi

export JFR_RECORDINGS_DIR="${JFR_RECORDINGS_DIR:-${PROJECT_ROOT}/recordings}"

exec java -jar "${JAR_FILE}" "$@"
