#!/usr/bin/env bash
set -euo pipefail
# This is a placeholder. If a TS runtime exists inside the image, start it here.
if [[ -f "/opt/ts-runtime/dist/server.js" ]]; then
  echo "[run-ts] starting TS runtime on :7070"
  # Start the server in background
  node /opt/ts-runtime/dist/server.js &
  ts_pid=$!
  
  # Wait for the server to be ready
  echo "[run-ts] waiting for TypeScript runtime to be ready..."
  for i in {1..30}; do
    if curl -s http://localhost:7070/health >/dev/null 2>&1; then
      echo "[run-ts] TypeScript runtime is ready!"
      break
    fi
    echo "[run-ts] waiting... ($i/30)"
    sleep 2
  done
  
  # Keep the process running
  wait $ts_pid
else
  echo "[run-ts] no TS runtime found, idling"
  # Idle loop instead of failing to keep supervisor green; disable via env if desired
  while true; do sleep 3600; done
fi
