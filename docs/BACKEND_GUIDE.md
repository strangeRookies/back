# 백엔드 서버 개발 가이드 (BACKEND_GUIDE)

본 문서는 스마트 안전 관제 시스템의 백엔드(Java / Spring Boot 3.3) 서버 개발자를 위한 코드 구조 및 연동 명세서입니다.

---

## ☕ 백엔드 서버 역할 개요

백엔드 서버는 카메라(CCTV)의 관리 데이터(CRUD), 장비 연결 상태, 관제 구역 설정을 제어하며 AI 엔진으로부터 발행되는 이상행동 경보(MQTT)를 비동기로 받아 관계형 데이터베이스(PostgreSQL)에 영속화하고, 웹소켓(STOMP)을 통해 다수의 프론트엔드 관제기로 브로드캐스팅 라우팅 처리를 수행합니다.

---

## 📂 주요 패키지 구조

```text
strange_back/src/main/java/com/strange/safety/
├── alert/                   # 이상행동 경보 및 통계 관리 패키지
│   ├── controller/          # AlertEventController
│   ├── entity/              # AlertEvent (UNACKNOWLEDGED, ACKNOWLEDGED)
│   ├── repository/          # AlertEventRepository
│   └── service/             # AlertEventService
├── auth/                    # 개인/기업 로그인, 회원가입, SMS 토큰 및 JWT 인증 패키지
├── camera/                  # CCTV 정보 수집, ROI 구역 관리 패키지
│   ├── controller/          # CameraController, RoiConfigController
│   ├── entity/              # Camera, RoiConfig, CameraStatusLog
│   ├── repository/          # CameraRepository
│   └── service/             # CameraService, CameraStatusService
├── common/                  # 전역 예외 처리(GlobalExceptionHandler) 및 공통 응답 정의
├── event/                   # MQTT Subscribe 및 WebSocket STOMP 브로드캐스트 라우팅 패키지
│   ├── AlertBroadcastService.java       # WebSocket /topic/facility/{id}/alerts 송출 클래스
│   ├── AsyncEventProcessorService.java  # 비동기 경보/상태 분기 처리 서비스
│   └── MqttSafetyEventSubscriber.java   # Spring Integration MQTT 수신 인터페이스
├── facility/                # 관제 대상 구역(Facility) 패키지
└── incident/                # 경보 확인 및 녹화 이력(IncidentRecordingRecord) 패키지
```

---

## 💾 핵심 엔티티 구조 (Entity)

### 1. Camera (개인용/일반 카메라) & CorporateCamera (기업용 카메라)
* **테이블명:** `cameras`, `corporate_cameras` (extends `BaseEntity`)
* **핵심 필드:**
  * `cameraLoginId` (String): 프론트/AI 연동용 카메라 식별 아이디 (예: `cam_01`, `cam_02` 등)
  * `cameraName` (String): 카메라 이름
  * `rtspUrl` (String): YOLO 분석용 실시간 스트림 주소
  * `aiEnabled` (Boolean): AI 감지 활성화 여부
  * `sourceType` (Enum): `REAL` / `VIRTUAL`

### 2. AlertEvent (알림 이벤트)
* **테이블명:** `alert_events`
* **핵심 필드:**
  * `id` (Long): PK
  * `camera` (Camera): 다대일 매핑 (연관된 CCTV 정보)
  * `eventType` (String): 이상행동 종류 (`faint` 등)
  * `severity` (Enum): `INFO`, `HIGH`, `CRITICAL`
  * `status` (Enum): `UNACKNOWLEDGED` (미확인), `ACKNOWLEDGED` (확인 완료)
  * `detectedAt` (LocalDateTime): AI가 탐지한 최초 시각

### 3. IncidentRecordingRecord (녹화 요청 기록)
* **테이블명:** `incident_recording_records`
* **핵심 필드:**
  * `id` (Long): PK
  * `eventId` (String): 위험 이벤트 고유 해시 키
  * `recordingStatus` (Enum): `RECORDING_REQUESTED` (녹화 대기), `COMPLETED` (녹화본 생성됨)

---

## 🔄 핵심 데이터 라우팅 및 처리 메커니즘

### 1. 카메라 정보 관리 (CRUD)
* 관리자가 카메라를 생성(`POST /api/cameras`)할 때 `cameraLoginId` 명명법을 고유하게 등록하며, AI 서비스는 구동될 때 백엔드 활성 카메라 리스트 API(`GET /api/cameras/active`)를 참조하여 스트림 스레드 풀을 동적으로 설정합니다.

### 2. MQTT 수신 및 비동기 파이프라인
* **구독 진입:** `MqttSafetyEventSubscriber`가 `mqttInputChannel`로 수신되는 메시지를 감지합니다.
* **비동기 분기:** `eventProcessingExecutor` 풀을 기반으로 `@Async` 어노테이션이 붙은 `AsyncEventProcessorService.processEvent(SafetyEventDto)`가 동작하여, DB 저장 시 발생하는 블로킹이 브로커 수신 처리에 영향을 주지 않도록 격리시킵니다.
* **DB 영속화:** `AlertEventService.createEvent(event)`가 호출되어 이상 경보 상태를 영속 데이터베이스에 저장합니다.

### 3. WebSocket STOMP 실시간 방송
* **송출 클래스:** `AlertBroadcastService.java`
* **메커니즘:** 스프링의 `SimpMessagingTemplate`를 주입받아 `/topic/facility/{id}/alerts` 또는 `/topic/company/{id}/alerts` 주소로 역직렬화된 JSON 데이터를 `convertAndSend` 형태로 송출합니다.
* **관련 설정:** `WebSocketConfig`에서 STOMP 엔드포인트 `/ws`를 선언하고, 프론트 포트 CORS 우회를 위한 `allowedOriginPatterns` 허용을 처리합니다.

---

## ⚙️ 로컬 데이터베이스 및 환경 설정

* **DBMS:** PostgreSQL 16+
* **로컬 연결 DB 주소:** `jdbc:postgresql://localhost:5432/strange_safety` (Docker Compose 기본 포트)
* **핵심 설정 파일:** `src/main/resources/application.yml`
* **설정 오버라이딩:** 프로덕션 빌드나 AWS RDS 연결 시 런타임 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MQTT_HOST` 등)를 주입받아 동적으로 설정이 대체됩니다.

---

## 🪵 로그 모니터링 및 트러블 진단

스프링 부트 서버의 출력 로그 레벨은 기본적으로 `INFO`로 구성되어 있습니다.
```bash
# 실시간 백엔드 수신 로그 감시
tail -f strange_back_run.log | grep -E "MQTT Async|Broadcasting"
```
* **수신 성공 로그 포맷:** `Received MQTT message from topic safety/events: {...}`
* **웹소켓 방송 성공 로그 포맷:** `Broadcasting safety event to /topic/facility/1/alerts: type=faint, cameraId=1, severity=HIGH`

---

## ⚠️ 백엔드 수정 시 주의할 점
1. **MQTT 클라이언트 ID 충돌 주의:** `application.yml`에서 백엔드의 MQTT Client ID를 `spring-backend-${random.uuid}` 형태로 동적 지정하고 있습니다. 만약 이를 고정 주소로 임의 수정하여 다중 노드를 띄울 경우, 브로커 단에서 연결 세션이 계속 튕겨나가는 루프 현상이 초래됩니다.
2. **WebSocket STOMP 의존성 주의:** 외부 라이브러리 추가 시 프론트엔드의 커스텀 STOMP 파서가 이해할 수 있는 표준 프레임 규격(`CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE` 헤더)을 해치지 않아야 합니다.
3. **FCM 비활성화 기본 정책:** FCM 푸시 서비스(`fcm.enabled`) 기본값은 `false` (Mock) 입니다. 실 상용화 시 Firebase Admin API 키 JSON 파일을 추가하고 환경변수를 활성화해야 합니다.
