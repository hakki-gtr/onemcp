#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SERVER_DIR="${ROOT_DIR}/packages/server"
JAR_PATH="${SERVER_DIR}/target/onemcp-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "${JAR_PATH}" ]; then
  echo "[graph-query] Building server package (jar-with-dependencies)..."
  (cd "${SERVER_DIR}" && mvn -q -DskipTests package > /dev/null)
fi

export ARANGODB_ENABLED="${ARANGODB_ENABLED:-true}"
export GRAPH_QUERY_DRIVER="${GRAPH_QUERY_DRIVER:-arangodb}"
export SERVER_PORT="${SERVER_PORT:-18080}"

JAVA_MAIN="com.gentoro.onemcp.scripts.GraphQueryScript"

exec java -cp "${JAR_PATH}" "${JAVA_MAIN}" "$@"

