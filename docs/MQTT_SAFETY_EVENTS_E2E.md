# MQTT Safety Events End-to-End Check

This backend receives AI safety events from MQTT topic `safety/events`, persists them as `alert_events`, and broadcasts them to the frontend over STOMP topic `/topic/alerts`.

## Repos And Scope

- Backend repo in this workspace: `strange_back`
- Frontend repo in this workspace: `strange_front`
- AI YOLO/LSTM/RTSP logic is not changed by this check.
- Redis Pub/Sub is not part of this MQTT safety event path.

The user-provided GPU path `~/yolo_training/back` was not available in this local workspace. The inspected backend is `strange_back`, which contains `build.gradle`, `settings.gradle`, and `src`.

## MQTT Broker

Host-run backend:

```bash
export MQTT_HOST=localhost
export MQTT_PORT=1883
export MQTT_TOPIC=safety/events
```

Docker-run backend on the same Docker network as Mosquitto:

```bash
export MQTT_HOST=strange-mosquitto
export MQTT_PORT=1883
export MQTT_TOPIC=safety/events
```

Alternatively set the full broker URL:

```bash
export MQTT_BROKER_URL=tcp://localhost:1883
```

Use `MQTT_USERNAME` and `MQTT_PASSWORD` only when the broker requires credentials. Do not commit real credentials.

## Backend Run

There is no backend `docker-compose.yml` in this repo. Use Gradle `bootRun` for local development:

```bash
cd ~/yolo_training/back
export SPRING_PROFILES_ACTIVE=local
export MQTT_HOST=localhost
export MQTT_PORT=1883
export MQTT_TOPIC=safety/events
export DB_URL=jdbc:postgresql://localhost:5432/strange_safety
export DB_USERNAME=<db-user>
export DB_PASSWORD=<db-password>
./gradlew bootRun
```

When running backend in Docker, use a Docker-network broker host:

```bash
export MQTT_HOST=strange-mosquitto
```

## Broker Verification Commands

```bash
docker ps | grep mosquitto
docker exec -it strange-mosquitto mosquitto_sub -h localhost -p 1883 -t "safety/events" -v
docker exec -it strange-mosquitto mosquitto_pub -h localhost -p 1883 -t "safety/events" -m '{"camera_id":"cam1","type":"Faint","event_type":"Faint","severity":"WARNING","confidence":0.83,"bbox":[100,120,220,360],"track_id":1,"timestamp":"2026-06-08T16:30:00+09:00"}'
```

## Test Publish Script

From the backend repo:

```bash
chmod +x scripts/publish_test_safety_event.sh
MQTT_HOST=localhost MQTT_PORT=1883 ./scripts/publish_test_safety_event.sh
```

Override fields:

```bash
CAMERA_ID=cam1 EVENT_TYPE=Faint SEVERITY=WARNING CONFIDENCE=0.83 TRACK_ID=1 \
  MQTT_HOST=localhost MQTT_PORT=1883 ./scripts/publish_test_safety_event.sh
```

`camera_id=cam1` is normalized by the backend to `cam_01`. The database must contain a camera row whose `camera_login_id` is `cam_01`.

## Backend Logs To Confirm

Expected MQTT connection and subscribe logs:

```text
Connecting to MQTT broker: brokerUrl=..., clientId=..., topic=safety/events
Subscribed to MQTT topic: safety/events
```

Expected receive log:

```text
Received MQTT safety event from topic safety/events: {...}
```

Expected DB save log:

```text
Saved MQTT safety alert event: alertEventId=..., cameraLoginId=cam_01, scenarioType=SYNCOPE, severity=WARNING, confidence=0.83, trackId=1
```

Mapping failure logs:

```text
Failed to map MQTT safety event camera_id: rawCameraId=cam1, normalizedCameraLoginId=cam_01
Failed to map MQTT safety event type: rawType=..., scenarioType=...
```

## Database Preconditions

Scenario seed is handled by `ScenarioDataInitializer` when the scenario table is empty. `Faint` maps to `ScenarioType.SYNCOPE`.

Camera seed is not automatic. Confirm a matching camera exists:

```bash
psql "$DB_URL" -U "$DB_USERNAME" -c "select camera_id, camera_login_id, status from cameras where camera_login_id in ('cam_01','cam_02','cam_03','cam_04');"
```

If `cam_01` is missing, create/register a camera through the backend camera API or a local development seed before publishing the test event.

## Database Save Check

After publishing:

```bash
psql "$DB_URL" -U "$DB_USERNAME" -c "select ae.alert_event_id, c.camera_login_id, s.scenario_type, ae.severity, ae.confidence_score, ae.bounding_box_data, ae.detected_at, ae.created_at from alert_events ae join cameras c on c.camera_id = ae.camera_id join scenarios s on s.scenario_id = ae.scenario_id order by ae.alert_event_id desc limit 5;"
```

The test payload should create:

- `camera_login_id=cam_01`
- `scenario_type=SYNCOPE`
- `severity=WARNING`
- `confidence_score=0.83`
- `bounding_box_data` containing `bbox` and `trackId`

## Frontend Delivery Check

Backend broadcast path:

- STOMP endpoint: `/ws`
- STOMP topic: `/topic/alerts`
- Broadcast service: `AlertBroadcastService.broadcast()`

Frontend receiving path in `strange_front`:

- `src/hooks/useAiEvents.ts` connects to `${VITE_BACKEND_BASE_URL}/ws`
- It subscribes to `/topic/alerts`
- It logs WebSocket status changes in the browser console.
- It normalizes `camera_id/cameraId`, `event_type/type`, `timestamp`, `severity`, `confidence`, `bbox`, and `track_id`.

Frontend run/check:

```bash
cd ../strange_front
export VITE_BACKEND_BASE_URL=http://localhost:8080
npm run dev
```

Open the monitoring dashboard and check browser console for:

```text
[useAiEvents] Connecting to WebSocket:
[STOMP] Connected to backend
[useAiEvents] WebSocket status changed: connected
```

Then publish the MQTT test event and confirm the dashboard receives an AI danger event. If the frontend repo is not present on the GPU PC, backend STOMP broadcast is the last verifiable point there.

## Actual AI Event Connection

Run the backend with the same broker used by the AI publisher:

```bash
cd ~/yolo_training/back
MQTT_HOST=localhost MQTT_PORT=1883 MQTT_TOPIC=safety/events ./gradlew bootRun
```

If backend is containerized on the Mosquitto Docker network:

```bash
MQTT_HOST=strange-mosquitto MQTT_PORT=1883 MQTT_TOPIC=safety/events ./gradlew bootRun
```

Then run the AI publisher so it publishes real detection JSON to:

```text
safety/events
```
