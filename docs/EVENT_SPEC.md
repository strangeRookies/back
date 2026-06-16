# 실시간 이벤트 및 메시지 규격 정의서 (EVENT_SPEC)

본 문서는 AI 분석 엔진, 백엔드 중계 서버, 프론트엔드 관제 대시보드 간에 송수신되는 메시지 프로토콜 및 데이터 스키마 규격을 정의합니다.

---

## 📡 1. MQTT 이벤트 규격 (AI ➡ 백엔드)

AI 분석 엔진은 쓰러짐(Faint) 등의 이상행동을 감지하는 순간 MQTT Broker로 이벤트를 발행(Publish)합니다.

* **MQTT 토픽:** `safety/events`
* **발행 주기:** 실시간 감지 시 (연속 감지 임계 도달 후 Cooldown 10초 적용)
* **메시지 형식:** UTF-8 JSON

### MQTT 이벤트 Payload 예제 (Python AI 엔진 발행 기준)
```json
{
  "event_type": "faint",
  "camera_id": "cam_01",
  "camera_login_id": "cam_01",
  "frame_idx": 1824,
  "timestamp": 1780000000,
  "score": 0.87,
  "confidence": 0.91,
  "boxes": [],
  "bbox": [100, 150, 280, 390],
  "threshold": 0.3,
  "track_id": 7,
  "severity": "HIGH"
}
```

### 각 필드의 의미 및 사양
| 필드명 | 타입 | 필수 여부 | 설명 |
| :--- | :--- | :--- | :--- |
| `event_type` | String | 필수 | 이상행동 종류 (`faint` / `fall_detected` [확인 필요]) |
| `camera_id` | String | 필수 | 이벤트를 유발한 카메라 식별자 |
| `camera_login_id` | String | 선택 | 프론트와 매핑되는 카메라 로그인 식별 ID |
| `frame_idx` | Integer | 선택 | 이상행동이 판단된 동영상 스트림 상의 프레임 인덱스 |
| `timestamp` | Long | 필수 | 감지 시점의 Unix Epoch Timestamp (초 단위 또는 밀리초 단위) |
| `score` | Double | 선택 | 행동 분류기(LSTM)의 판별 점수 / 확률값 (0.0 ~ 1.0) |
| `confidence` | Double | 필수 | YOLO 바운딩 박스 검출 신뢰도 (0.0 ~ 1.0) |
| `bbox` | Array | 선택 | 감지 대상자의 바운딩 박스 좌표 `[x_min, y_min, x_max, y_max]` |
| `track_id` | String/Int | 필수 | ByteTrack 또는 Fallback Tracker가 할당한 객체 추적 고유 ID |
| `severity` | String | 필수 | 이벤트 긴급 위험성 단계 (`HIGH` / `CRITICAL` / `INFO` 등) |

---

## ⚙️ 2. 백엔드 수신 후 처리 흐름

1. **MQTT 수신 및 파싱:** 백엔드 서버의 `MqttSafetyEventSubscriber` 클래스가 `safety/events` 토픽을 상시 구독하다가 메시지가 수신되면 `SafetyEventDto`로 언마샬링합니다.
2. **카메라 대조 및 검증:** `SafetyEventDto`에 기재된 `camera_id` 또는 `camera_login_id`를 기준으로 백엔드 데이터베이스의 `cameras` 테이블과 매칭하여 실제 관리 대상 카메라인지 확인합니다.
3. **이벤트 DB 영속화:**
   * `alert_events` 테이블에 기록을 삽입합니다.
   * 이때 상태(`status`)는 최초 생성 상태인 **`UNACKNOWLEDGED`** 또는 **`ACTIVE`**로 저장됩니다.
4. **WebSocket STOMP 송출:**
   * 데이터베이스 저장 완료 즉시 WebSocket 브로커로 전달되어 `/topic/alerts` 주소를 구독하고 있는 모든 세션에 실시간 경보 메시지를 송출합니다.

---

## 💻 3. Frontend WebSocket 수신 규격 (백엔드 ➡ 프론트)

웹 프론트엔드 대시보드는 백엔드 WebSocket 서버에 연결하여 STOMP 메시지 브로드캐스트를 받아 화면을 업데이트합니다.

* **STOMP Endpoint:** `/ws`
* **구독 목적지(Topic):** `/topic/alerts`
* **메시지 데이터 파서 유연성:** 프론트엔드의 `useAiEvents.ts` 내부 `normalizeRawPayload` 함수가 Python(snake_case)과 Java(camelCase) 스타일 필드명을 모두 호환할 수 있게 정규화 단계를 거칩니다.

### 프론트엔드 수신 데이터 정규화 매핑표
프론트엔드에서 최종 파싱된 `AiEvent` 인터페이스는 다음 데이터를 제공합니다.

```typescript
export interface AiEvent {
  readonly camera_id: string;      // raw.camera_id / raw.cameraId / raw.cameraLoginId 중 바인딩
  readonly camera_login_id?: string;
  readonly frame_idx: number;
  readonly timestamp: number;       // Unix epoch ms 단위로 정규화됨
  readonly event_type: string;      // raw.event_type 또는 raw.type
  readonly score: number;
  readonly confidence: number;
  readonly boxes: readonly Record<string, unknown>[];
  readonly bbox: unknown;
  readonly threshold: number;
  readonly track_id: string | null;
  readonly severity: string;
}
```

---

## 🚨 4. 관제 시스템 상태값 규격 (Status Codes)

프론트엔드 및 백엔드 간에 공유되는 관제 시스템 전반의 카메라/위험 상태값 분류 기준입니다.

| 상태 상수 (Enum) | 프론트 표시 명칭 | 설명 |
| :--- | :--- | :--- |
| **`UNKNOWN`** | 미정 / 식별 불가 | 시스템 초기화 중이거나 카메라 소스 유형이 정의되지 않아 모니터링이 불가한 임시 상태 |
| **`NORMAL`** | 정상 (Normal) | 카메라 연동 및 분석 루프가 원활히 작동 중이며 감지된 이상 행동이 없는 안전 상태 |
| **`DANGER`** | 위험 경보 (Danger) | LSTM 분류 모델이 임계치 이상의 쓰러짐/실신을 감지하여 알람 사운드 및 아이콘 펄스가 켜진 경보 상태 |
| **`ERROR`** | 에러 (Error) | RTSP 연결 실패, 스트림 유실, 또는 백엔드와 소켓 통신 차단 등 기술적 오동작 상태 |
