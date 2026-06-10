#!/usr/bin/env bash
set -euo pipefail

MQTT_HOST="${MQTT_HOST:-localhost}"
MQTT_PORT="${MQTT_PORT:-1883}"
MQTT_TOPIC="${MQTT_TOPIC:-safety/events}"
CAMERA_ID="${CAMERA_ID:-cam1}"
EVENT_TYPE="${EVENT_TYPE:-Faint}"
SEVERITY="${SEVERITY:-WARNING}"
CONFIDENCE="${CONFIDENCE:-0.83}"
TRACK_ID="${TRACK_ID:-1}"
TIMESTAMP="${TIMESTAMP:-2026-06-08T16:30:00+09:00}"
BBOX="${BBOX:-[100,120,220,360]}"
MQTT_PUB_BIN="${MQTT_PUB_BIN:-mosquitto_pub}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  PYTHON_BIN="python"
fi
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "ERROR: python3 or python is required to build the test JSON payload." >&2
  exit 1
fi

PAYLOAD=$(
  "$PYTHON_BIN" - "$CAMERA_ID" "$EVENT_TYPE" "$SEVERITY" "$CONFIDENCE" "$TRACK_ID" "$TIMESTAMP" "$BBOX" <<'PY'
import json
import sys

camera_id, event_type, severity, confidence, track_id, timestamp, bbox = sys.argv[1:]
print(json.dumps({
    "camera_id": camera_id,
    "type": event_type,
    "event_type": event_type,
    "severity": severity,
    "confidence": float(confidence),
    "bbox": json.loads(bbox),
    "track_id": int(track_id) if track_id.isdigit() else track_id,
    "timestamp": timestamp,
}, separators=(",", ":")))
PY
)

echo "Publishing MQTT safety event:"
echo "  host=$MQTT_HOST"
echo "  port=$MQTT_PORT"
echo "  topic=$MQTT_TOPIC"
echo "  camera_id=$CAMERA_ID"
echo "  type=$EVENT_TYPE"
echo "  severity=$SEVERITY"
echo "  confidence=$CONFIDENCE"

"$MQTT_PUB_BIN" -h "$MQTT_HOST" -p "$MQTT_PORT" -t "$MQTT_TOPIC" -m "$PAYLOAD"
echo "Published: $PAYLOAD"
