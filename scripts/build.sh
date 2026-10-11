#!/usr/bin/env bash
set -euo pipefail

# Build script for jfr-performance-mcp
echo "=================================================="
echo "Building jfr-performance-mcp"
echo "=================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "Running: mvn clean package"
mvn clean package

echo ""
echo "Build complete. Output JAR:"
ls -lh "${PROJECT_ROOT}"/target/jfr-performance-mcp-*.jar
