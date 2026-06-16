# 스마트 안전 관제 시스템 개발자 가이드 (PROJECT_GUIDE)

본 문서는 실시간 CCTV 영상 분석을 기반으로 작업장의 이상행동(실신, 쓰러짐 등)을 감지하고 전파하는 **스마트 안전 관제 시스템**의 통합 개발자 시작 문서입니다. 이 프로젝트는 AI 엔진, 백엔드 서버, 프론트엔드 대시보드 및 인프라의 유기적인 연동으로 구성되어 있습니다.

---

## 📌 프로젝트 개요

### 1. 프로젝트 목적
CCTV 스트리밍 영상을 실시간으로 분석하여 작업자의 이상행동(쓰러짐/실신 등)을 자동으로 감지하고, 관제원에게 즉각 경보를 전송하여 골든타임을 확보하고 산업 재해 피해를 최소화하는 것을 목적으로 합니다.

### 2. 해결하려는 문제
* **관제 공백 해소:** 다수의 CCTV를 관제원이 24시간 실시간으로 감시할 때 발생하는 인간적인 피로도와 집중력 한계 극복.
* **신속한 상황 전파:** 위험 상황 발생 시 즉각적인 감지와 함께 시각/청각적 경고 및 이력 데이터베이스 기록을 통해 신속한 현장 조치 지원.
* **네트워크 효율성 및 지연성 개선:** 높은 용량의 고해상도 영상을 그대로 분석 서버로 가져오는 대신, RTSP 스트림 서버(MediaMTX)를 통한 효율적인 중계와 경량화된 AI 모델 적용으로 저지연 구현.

### 3. 전체 시스템 한 줄 요약
> **"RTSP 카메라 스트림을 YOLO & LSTM AI 모델로 분석하여 쓰러짐을 감지하고, MQTT와 WebSocket을 거쳐 리액트 관제 화면에 실시간 경보를 전송하는 스마트 안전 관리 시스템"**

---

## 🏢 시스템 모듈별 역할 요약

프로젝트는 크게 3개의 서브 프로젝트로 구분되어 독립적으로 개발 및 관리됩니다.

| 모듈명 | 위치 | 주 프로그래밍 언어 / 스택 | 핵심 역할 및 설명 |
| :--- | :--- | :--- | :--- |
| **strange_ai** | [strange_ai](file:///c:/Users/user/Documents/최종%20쉴더스/strange_ai) | Python 3.10+ / PyTorch, YOLO26n-pose, LSTM | RTSP 스트림을 수신해 사람의 키포인트(Pose)를 추출하고, 프레임 시퀀스를 LSTM으로 분석해 Faint/Normal 여부를 판별하여 MQTT Broker로 발행 |
| **strange_back** | [strange_back](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back) | Java 21 / Spring Boot 3.3, JPA, Spring WebSocket | MQTT 이벤트를 구독하여 DB(PostgreSQL)에 기록하고, WebSocket STOMP 프로토콜을 통해 접속된 모든 웹 관제 콘솔에 실시간 브로드캐스팅 |
| **strange_front** | [strange_front](file:///c:/Users/user/Documents/최종%20쉴더스/strange_front) | TypeScript / React 18, Vite, Custom STOMP Client | 실시간 WebSocket 경보를 청취하여 평면도 상 노드 알림과 사이렌을 울리며, MediaMTX의 HLS 스트림을 웹 브라우저 상에 출력 |
| **strange_infra** | [strange_infra](file:///c:/Users/user/Documents/최종%20쉴더스/strange_infra) | Docker Compose | 로컬 개발 환경용 MQTT Broker(EMQX/Mosquitto) 및 PostgreSQL 인프라 컨테이너 통합 관리 |

---

## 🔄 전체 데이터 흐름 요약
1. **RTSP 영상 입력:** CCTV 또는 비디오 파일에서 생성된 RTSP 스트림이 MediaMTX로 송출됩니다.
2. **AI 분석:** AI 엔진이 RTSP 프레임을 수신하여 YOLO26n-pose 모델로 작업자의 키포인트를 추출하고, 이를 30프레임 버퍼로 모아 LSTM 모델로 쓰러짐(Faint)을 판단합니다.
3. **이벤트 발행:** 쓰러짐 판단 시 MQTT Broker(`safety/events` 토픽)에 JSON 형태로 이벤트를 발행합니다.
4. **백엔드 수신 및 저장:** Spring Boot가 MQTT 이벤트를 구독하여 PostgreSQL DB에 저장합니다.
5. **실시간 알림 전송:** 저장 완료와 동시에 WebSocket STOMP endpoint (`/topic/alerts`)로 경보를 브로드캐스팅합니다.
6. **프론트엔드 시각화:** React 웹 대시보드가 알림을 수신하여 2D SVG 평면도에 경보를 깜빡이고, 알림 사운드를 재생하며, HLS 비디오 스트림을 화면에 로드합니다.

---

## 🚀 전체 실행 순서 요약
로컬 개발 환경에서 시스템을 구동하는 단계별 요약입니다. (상세 내용은 [RUNBOOK.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/RUNBOOK.md)를 참조하십시오.)

1. **MQTT & PostgreSQL 실행:** `docker compose`로 인프라를 구동합니다.
2. **MediaMTX 실행:** RTSP/HLS 중계 서버를 실행합니다.
3. **Backend 실행:** Spring Boot 서버(`port 8080`)를 구동합니다.
4. **Frontend 실행:** React 개발 서버(`port 5173`)를 구동합니다.
5. **AI 추론 실행:** 시뮬레이션용 RTSP 송출 및 AI LSTM 추론 스크립트를 구동합니다.
6. **카메라 등록:** 백엔드 API 또는 프론트 화면을 통해 분석 대상 카메라 정보(`cameraLoginId` 등)를 등록합니다.
7. **이벤트 발생 확인:** 웹 대시보드의 실시간 경보 창 및 알림 사운드를 통해 감지 여부를 확인합니다.

---

## 🔌 주요 포트 요약

| 포트 번호 | 서비스명 | 프로토콜 | 설명 |
| :--- | :--- | :--- | :--- |
| **8554** | MediaMTX RTSP | RTSP | 실시간 카메라 영상 스트리밍 수신 및 송출용 포트 |
| **8888** | MediaMTX HLS | HTTP (HLS) | 프론트엔드 브라우저 렌더링을 위한 HLS 스트림 중계 포트 |
| **8080** | Spring Boot Backend | HTTP / WebSocket | 카메라 CRUD API 및 WebSocket STOMP(/ws) 포트 |
| **5173** | React Web Dashboard | HTTP | 프론트엔드 로컬 개발 서버 포트 (Vite 기본값) |
| **1883** | MQTT Broker | TCP (MQTT) | AI 엔진과 백엔드 서버 간의 이벤트 메시지 송수신 포트 |
| **18083** | EMQX Dashboard | HTTP | MQTT 브로커 상태 모니터링 대시보드 포트 (EMQX 사용 시) |
| **5432** | PostgreSQL | TCP (SQL) | 관제 시스템 관리 데이터베이스 포트 |
| **8010~8013** | AI Overlay Server | HTTP (MJPEG) | 실시간 YOLO 뼈대 분석 결과 레이어가 포함된 영상 중계 포트 |

---

## 📂 세부 문서 링크 목록
다음 링크들을 통해 각 영역별 상세 내용을 확인하실 수 있습니다.

* **시스템 아키텍처 및 상세 흐름:** [ARCHITECTURE.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/ARCHITECTURE.md)
* **로컬 개발 환경 실행 가이드:** [RUNBOOK.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/RUNBOOK.md)
* **문제 해결 가이드:** [TROUBLESHOOTING.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/TROUBLESHOOTING.md)
* **이벤트 프로토콜 규격:** [EVENT_SPEC.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/EVENT_SPEC.md)
* **백엔드 API 규격:** [API_SPEC.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/API_SPEC.md)
* **AI 엔진 개발 가이드:** [AI_GUIDE.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_ai/docs/AI_GUIDE.md)
* **백엔드 개발 가이드:** [BACKEND_GUIDE.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/BACKEND_GUIDE.md)
* **프론트엔드 개발 가이드:** [FRONTEND_GUIDE.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_front/docs/FRONTEND_GUIDE.md)
* **AI 모델 학습 및 전처리 전략:** [MODEL_TRAINING_STRATEGY.md](file:///c:/Users/user/Documents/최종%20쉴더스/strange_back/docs/MODEL_TRAINING_STRATEGY.md)

---

## 🛠️ 프로젝트 진행 상황

### 1. 현재 완성된 부분
* **AI 모델 검증 및 선별:** YOLOv8-pose, YOLOv11-pose, YOLO26n-pose 비교 벤치마크 결과 Downstream LSTM 성능이 가장 우수하고 determinism이 보장된 `YOLO26n-pose` 모델 확정 및 sequence buffer 구현 완료.
* **실시간 AI 추론 파이프라인:** RTSP 연결을 지원하는 `run_rtsp_inference.py` 및 프론트 시각화용 MJPEG Overlay를 제공하는 `serve_ai_overlay.py` 구현 완료.
* **백엔드 실시간 중계:** MQTT Broker에 전송된 `safety/events` 메시지를 구독하고, JPA를 통해 PostgreSQL DB에 저장 후 WebSocket STOMP (`/topic/alerts`)로 프론트에 전송하는 구조 구현 완료.
* **프론트엔드 대시보드:** 커스텀 경량 STOMP 웹소켓 클라이언트, 2D SVG 기반 도면 노드 매핑 및 클릭 시 관련 HLS 카메라 스트림(`Hls.js`) 자동 렌더링 완료.

### 2. 아직 남은 부분 (Roadmap)
* **다양한 이상행동 추가:** 현재 완성된 쓰러짐(Faint) 모델 외에 **낙상(Fall)** 및 **현장 폭력(Fight)** 동작 감지 기능 확장 필요.
* **AI 링버퍼 기반 이벤트 클립 자동 녹화:** 위험 감지 발생 시 전후 150프레임(총 300프레임) 단위의 녹화본 동영상을 저장 및 CDN 업로드하여 관제 대시보드 이력 보기에서 재확인할 수 있는 파이프라인 연동.
* **모바일 푸시 알림 연동:** [확인 필요] `FcmService`를 활용한 모바일 어플리케이션 푸시 발송 로직 추가 및 FCM 인증 정보 주입 자동화.
