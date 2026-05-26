# Strange Back

Spring Boot backend for the Smart Safety Monitoring System MVP.

## Realtime Pipeline

```text
Python Edge AI Mock Publisher
-> MQTT Broker (Mosquitto) safety/events
-> Spring Boot MQTT Subscriber
-> WebSocket STOMP /topic/alerts
-> React Frontend
```

## MQTT To WebSocket Alerts

The backend subscribes to the MQTT topic `safety/events`, maps each JSON payload to `SafetyEventDto`, and broadcasts it to the STOMP topic `/topic/alerts`.

The previous Redis Pub/Sub subscriber is not used in the current MQTT MVP.

### Event Shape

```json
{
  "type": "fall_detected",
  "camera_id": "cam_01",
  "timestamp": "2026-05-26T10:00:00Z",
  "severity": "HIGH",
  "message": "Fall detected from mock edge AI",
  "source": "edge-ai-mock"
}
```

### Environment Variables

```text
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_CLIENT_ID=safety-backend
MQTT_TOPIC=safety/events
MQTT_RECONNECT_DELAY_MS=5000
SERVER_PORT=8080
WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000,http://localhost:5173
```

### Run

Start Mosquitto first:

```bash
docker compose up -d mosquitto
```

Run the backend:

```bash
./gradlew bootRun
```

On Windows without the Gradle wrapper:

```bash
gradle bootRun
```

### WebSocket Contract

```text
STOMP endpoint: /ws
Subscribe topic: /topic/alerts
MQTT topic: safety/events
```

### Manual Verification

1. Start Mosquitto from `strange_infra`.
2. Start this Spring Boot backend.
3. Run `strange_ai/mock_edge_ai.py`.
4. Check backend logs for MQTT `safety/events` receive logs.
5. Connect a STOMP client or the React frontend to `/ws`.
6. Subscribe to `/topic/alerts`.
7. Confirm safety event JSON messages arrive in real time.

### Security TODO

- Add MQTT username/password, certificates, or ACLs before production deployment.
- Do not commit real passwords, API keys, certificates, or private broker credentials.

### Scope

- This stage does not implement React UI changes.
- This stage does not implement DB persistence, JWT, RBAC, FCM, or external notifications.
- Secrets, passwords, API keys, personal videos, and private test media are not stored in this repository.
