# 백엔드 API 규격 정의서 (API_SPEC)

본 문서는 스마트 안전 관제 시스템의 백엔드(Java/Spring Boot) 서버가 제공하는 RESTful API 사양을 명시합니다. 모든 API 요청과 응답 형식은 JSON 프로토콜을 사용합니다.

---

## 📷 1. 카메라 관리 API (Camera Management)

| Method | URL | 설명 | Request Payload | Response Payload |
|---|---|---|---|---|
| **POST** | `/api/cameras` <br> `/api/facilities/{facilityId}/cameras` | 새 카메라 등록 | [CreateCameraRequest](#CreateCameraRequest) | [CameraResponse](#CameraResponse) |
| **GET** | `/api/cameras` <br> `/api/facilities/{facilityId}/cameras` | 카메라 목록 조회 | N/A | List of [CameraResponse](#CameraResponse) |
| **GET** | `/api/cameras/active` | AI 분석 활성화된 카메라만 조회 | N/A | List of [CameraResponse](#CameraResponse) |
| **PUT** | `/api/cameras/{cameraId}` | 기존 카메라 상세 설정 수정 | [UpdateCameraRequest](#UpdateCameraRequest) | [CameraResponse](#CameraResponse) |
| **DELETE** | `/api/cameras/{cameraId}` | 카메라 연동 해제 및 삭제 | N/A | Empty success response |

### DTO 스키마 명세

#### CreateCameraRequest
```json
{
  "cameraLoginId": "cam_01",
  "cameraName": "A구역 작업장 동편",
  "cameraSerialNumber": "SN-CCTV-90924",
  "cameraPassword": "password123",
  "rtspUrl": "rtsp://localhost:8554/cam_01",
  "locationDescription": "A동 조립설비 2호 라인 인근",
  "aiEnabled": true,
  "sourceType": "VIRTUAL"
}
```

#### UpdateCameraRequest
```json
{
  "cameraName": "A구역 작업장 동편 (수정)",
  "cameraSerialNumber": "SN-CCTV-90924",
  "cameraPassword": "new-password",
  "rtspUrl": "rtsp://localhost:8554/cam_01",
  "locationDescription": "A동 조립설비 2호 라인 인근 벽면",
  "aiEnabled": true,
  "sourceType": "VIRTUAL"
}
```

#### CameraResponse
```json
{
  "id": 1,
  "cameraLoginId": "cam_01",
  "cameraName": "A구역 작업장 동편",
  "cameraSerialNumber": "SN-CCTV-90924",
  "rtspUrl": "rtsp://localhost:8554/cam_01",
  "locationDescription": "A동 조립설비 2호 라인 인근",
  "aiEnabled": true,
  "sourceType": "VIRTUAL",
  "createdAt": "2026-06-16T09:00:00Z",
  "updatedAt": "2026-06-16T09:00:00Z"
}
```

---

## 🚨 2. 이상행동 경보 이벤트 API (Alert Events)

| Method | URL | 설명 | Request Parameters | Response Payload |
|---|---|---|---|---|
| **GET** | `/api/facilities/{facilityId}/alert-events` | 특정 구역의 알림 이벤트 목록 페이징 조회 | `severity`, `status`, `dateFrom`, `dateTo`, `page`, `size`, `sort` | Page wrapper of [AlertEventResponse](#AlertEventResponse) |
| **GET** | `/api/alert-events/{alertEventId}` | 경보 단건 상세 조회 | N/A | [AlertEventDetailResponse](#AlertEventDetailResponse) |
| **PATCH** | `/api/alert-events/{alertEventId}/acknowledge` | 경보 단건 확인 처리 | N/A | [AlertEventResponse](#AlertEventResponse) |
| **GET** | `/api/facilities/{facilityId}/alert-events/stats` | 특정 기간 내 알림 통계 조회 | `dateFrom`, `dateTo` | [AlertStatsResponse](#AlertStatsResponse) |

#### AlertEventResponse
```json
{
  "id": 102,
  "cameraId": 1,
  "cameraLoginId": "cam_01",
  "eventType": "faint",
  "severity": "HIGH",
  "status": "UNACKNOWLEDGED",
  "detectedAt": "2026-06-16T09:30:00Z"
}
```

#### AlertStatsResponse
```json
{
  "totalCount": 24,
  "unacknowledgedCount": 3,
  "severityStats": {
    "HIGH": 18,
    "CRITICAL": 6
  }
}
```

---

## ⏺️ 3. 사건 확인 및 녹화 요청 API (Incident Acknowledge & Record)

관제 대시보드에서 쓰러짐 이벤트를 관제사가 클릭하여 "확인 및 녹화 요청"을 보낼 때 사용하는 전용 API입니다.

| Method | URL | 설명 | Request Payload | Response Payload |
|---|---|---|---|---|
| **POST** | `/api/incidents/{eventId}/acknowledge-and-record` | 이벤트 확인 및 비디오 녹화 요청 | [AcknowledgeIncidentRequest](#AcknowledgeIncidentRequest) | [IncidentRecordingRecord](#IncidentRecordingRecord) |

#### AcknowledgeIncidentRequest
```json
{
  "eventId": "camera-1:Faint:1780550000:7",
  "cameraId": "cam_01",
  "eventType": "Faint",
  "eventTimestamp": "2026-06-16T09:30:00Z",
  "preFrames": 150,
  "postFrames": 150,
  "totalFrames": 300,
  "status": "acknowledged",
  "reason": "관제원 확인 완료 - 현장 조치 중",
  "acknowledgedBy": "safety-user"
}
```

#### IncidentRecordingRecord
```json
{
  "id": 12,
  "eventId": "camera-1:Faint:1780550000:7",
  "cameraId": "cam_01",
  "eventType": "Faint",
  "status": "acknowledged",
  "recordingStatus": "RECORDING_REQUESTED",
  "requestedAt": "2026-06-16T09:31:00Z"
}
```

---

## 🔑 4. 사용자 인증 API (Authentication)

| Method | URL | 설명 | Request Payload | Response Payload |
|---|---|---|---|---|
| **POST** | `/api/auth/signup/individual` | 개인 회원가입 | [IndividualSignupRequest](#IndividualSignupRequest) | [SignupResponse](#SignupResponse) |
| **POST** | `/api/auth/signup/corporate` | 기업 회원가입 | [CorporateSignupRequest](#CorporateSignupRequest) | [SignupResponse](#SignupResponse) |
| **POST** | `/api/auth/login` | 로그인 및 JWT 획득 | [LoginRequest](#LoginRequest) | [TokenResponse](#TokenResponse) |
| **POST** | `/api/auth/reissue` | Access Token 만료 시 Refresh Token으로 재발급 | [TokenReissueRequest](#TokenReissueRequest) | [TokenResponse](#TokenResponse) |
| **POST** | `/api/auth/logout` | 로그아웃 및 토큰 무효화 | [LogoutRequest](#LogoutRequest) | N/A |
| **POST** | `/api/auth/verifications/sms` | 회원가입 휴대폰 인증코드 발송 | SmsVerificationRequest | SmsVerificationResponse |
| **POST** | `/api/auth/verifications/sms/confirm` | 인증코드 일치 확인 | SmsVerificationConfirmRequest | SmsVerificationConfirmResponse |
| **GET** | `/api/auth/email-availability` | 이메일 중복 확인 검사 | Query `email` | AvailabilityResponse |

### 인증 저장소 정책

- Access Token은 기존처럼 JWT Bearer 토큰으로 응답한다.
- Refresh Token은 응답 JSON에 포함하지 않고 `REFRESH_TOKEN` HttpOnly 쿠키로 전달한다.
- 현재 유효한 Refresh Token은 Redis에 해시 값으로 저장하며 `jwt.refresh-token-expiration-ms` 기준 TTL로 자동 만료한다.
- 로그인 실패 횟수는 Redis `auth:login-fail:{email}` 카운터로 관리하며 5회 실패 시 5분 동안 `AUTH_LOGIN_LOCKED`로 제한한다.
- SMS 인증 완료 토큰은 Redis `auth:sms:verified:{tokenHash}`에 저장하며 15분 TTL과 1회성 소비를 적용한다.
- SMS 재전송 쿨다운과 일일 발송 제한은 Redis `auth:sms:cooldown:{phone}`, `auth:sms:daily:{phone}:{yyyyMMdd}` 카운터로 관리한다.
- Redis only 로그인 방식에서는 로그인 이력, 로그아웃 이력, 토큰 발급 기록, 관리자 감사 로그를 PostgreSQL에 저장하지 않는다.

---

## 💬 5. [확인 필요] 알림 관련 API (Notification - FCM)

FCM 푸시 알림 발송은 아래 구조로 설계되어 있습니다.

| Method | URL | 설명 | Request Payload | Response Payload |
|---|---|---|---|---|
| **POST** | `/api/notifications/fcm/token` <br> `[확인 필요]` | 사용자의 FCM 토큰 등록/갱신 | `{"fcmToken": "string"}` | Success / Fail |
| **POST** | `/api/notifications/test` <br> `[확인 필요]` | 테스트 푸시 알림 발송 | `{"userId": 1, "title": "test", "body": "test"}` | Success / Fail |
