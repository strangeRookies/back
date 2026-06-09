# Next Steps

## MQTT Safety Event E2E Verification

The backend MQTT safety event path is:

```text
Mosquitto safety/events
-> MqttSafetyEventSubscriber.messageArrived()
-> AlertEventService.createEvent(dto)
-> PostgreSQL alert_events
-> AlertBroadcastService.broadcast()
-> STOMP /topic/alerts
-> React useAiEvents()
```

Use `docs/MQTT_SAFETY_EVENTS_E2E.md` for the exact broker, backend, publish, DB, and frontend verification commands.

Important preconditions:

- Host-run backend should use `MQTT_HOST=localhost`.
- Docker-run backend should use `MQTT_HOST=strange-mosquitto` or the compose service name.
- Test payload `camera_id=cam1` is normalized to `cam_01`; DB must contain `cameras.camera_login_id='cam_01'`.
- `type=Faint` maps to `ScenarioType.SYNCOPE`; scenario seed is handled by `ScenarioDataInitializer` when scenarios are empty.
- Keep DB/MQTT credentials in environment variables. Do not hardcode secrets.

## AI Danger Alert Acknowledge Flow

The frontend safety dashboard now treats Confirm as a user acknowledgment of an AI danger alert. Confirm requests backend persistence through:

```text
POST /api/incidents/{eventId}/acknowledge-and-record
```

The request body must include:

```json
{
  "eventId": "camera-1:Faint:1780550000:7",
  "cameraId": "camera-1",
  "eventType": "Faint",
  "eventTimestamp": "2026-06-04T03:00:00Z",
  "preFrames": 150,
  "postFrames": 150,
  "totalFrames": 300,
  "status": "acknowledged",
  "reason": "acknowledged",
  "acknowledgedBy": "safety-user"
}
```

The backend saves the acknowledgment and creates an incident recording request with:

```text
recordingStatus=RECORDING_REQUESTED
```

No fake clip URL, video path, or encoded video artifact should be stored at this stage.

## Pending AI Clip Capture Contract

The actual 300-frame incident clip capture remains an AI service responsibility. The future AI service should:

- keep a per-camera ring buffer of recent frames,
- receive or poll `RECORDING_REQUESTED` incident requests,
- extract 150 frames before and 150 frames after the acknowledged event when available,
- produce the real clip artifact,
- update the incident record with the final clip metadata and terminal recording status.

Until that AI integration exists, the backend status means only that the recording request was accepted and stored.

## Later Work

- Replace the current in-memory incident recording repository with durable DB persistence.
- Add authenticated `acknowledgedBy` from the active user/session instead of relying only on the frontend payload.
- Add an AI service callback or polling API for clip capture completion.
- Add operational states such as `PENDING_CLIP_CAPTURE`, `CLIP_CAPTURED`, and `CLIP_CAPTURE_FAILED` when the clip service is implemented.
