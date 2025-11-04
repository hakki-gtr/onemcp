#!/usr/bin/env bash
set -euo pipefail
CFG="${OTEL_CONFIG:-/etc/otel-collector-config.yaml}"
if [[ ! -f "$CFG" ]]; then
  echo "[run-otel] missing $CFG, writing minimal file-sink config"
  cat > /etc/otel-collector-config.yaml <<'YAML'
receivers:
  otlp:
    protocols: {grpc: {}, http: {}}
exporters:
  file:
    path: /var/log/otel-collector.json
service:
  pipelines:
    traces: {receivers: [otlp], exporters: [file]}
    metrics: {receivers: [otlp], exporters: [file]}
    logs: {receivers: [otlp], exporters: [file]}
YAML
fi
echo "[run-otel] starting otelcol"
exec /usr/local/bin/otelcol --config "$CFG"
