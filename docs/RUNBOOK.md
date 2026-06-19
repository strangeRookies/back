# 로컬 개발 실행 가이드 (RUNBOOK)

본 문서는 로컬 개발 환경 또는 검증 환경에서 전체 시스템을 구동하기 위한 세부 단계를 정의합니다.

---

## 🛠️ 사전 준비 (Prerequisites)

시스템을 실행하기 전에 다음 소프트웨어들이 설치되어 있어야 합니다.

* **Docker & Docker Compose:** MQTT Broker(EMQX/Mosquitto) 및 PostgreSQL DB를 간편하게 띄우기 위해 필요합니다.
* **Java 21 (JDK):** Spring Boot 백엔드 구동용.
* **Node.js (v18 이상):** React 프론트엔드 개발 서버용.
* **Python 3.10+:** AI 분석 엔진 및 시뮬레이터 구동용.
* **MediaMTX:** RTSP/HLS 중계 서버 (Docker를 통해 자동으로 띄울 수도 있습니다).

---

## ⚙️ 주요 환경변수 설정

각 파트별 디렉터리에 `.env` 파일을 복사/생성하여 아래 환경변수를 사전에 세팅합니다.

### 1. 백엔드 (`strange_back/.env`)
```env
DB_URL=jdbc:postgresql://localhost:5432/strange_safety
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=local-development-jwt-secret-change-before-shared-use-32bytes
AES_SECRET_KEY=StrangeSafetyKey
SMS_ENABLED=false
SMS_PROVIDER=mock
```

### 2. 프론트엔드 (`strange_front/.env`)
```env
VITE_BACKEND_BASE_URL=http://localhost:8080
VITE_STREAM_BASE_URL=http://localhost:8888
VITE_CAMERA_1_STREAM_URL=http://localhost:8010/stream
VITE_CAMERA_2_STREAM_URL=http://localhost:8011/stream
VITE_CAMERA_3_STREAM_URL=http://localhost:8012/stream
VITE_CAMERA_4_STREAM_URL=http://localhost:8013/stream
```

### 3. AI 파이프라인 (`strange_ai_lstm` / `.env`)
```env
RTSP_URL=rtsp://localhost:8554/cam_01
CAMERA_ID=cam_01
DETECTOR_MODE=yolo
YOLO_MODEL=yolo26n-pose.pt
MQTT_HOST=localhost
MQTT_PORT=1883
MQTT_TOPIC=safety/events
```

---

## 🔄 전체 실행 순서 (Execution Workflow)

반드시 아래에 명시된 순서대로 서비스를 실행해 주십시오.

```text
1. MQTT 실행 ➡ 2. MediaMTX 실행 ➡ 3. Backend 실행 ➡ 4. Frontend 실행 ➡ 5. AI 추론 실행 ➡ 6. 카메라 등록 ➡ 7. 이벤트 발생 확인
```

---

### 1단계. MQTT 실행 (MQTT Broker)

로컬 개발 환경에서는 EMQX 또는 Mosquitto를 Docker Compose로 손쉽게 구동할 수 있습니다.

```bash
# strange_infra 또는 관련 경로에서 Docker Compose 실행
docker compose -f infra/local-mqtt/docker-compose.yml up -d
```
* **EMQX 대시보드 주소:** `http://localhost:18083` (기본 계정: `admin` / `public`)

---

### 2단계. MediaMTX 실행 (RTSP/HLS Stream Server)

실시간 RTSP 송출과 HLS 플레이어 연동을 지원하는 미디어 중계 서버를 실행합니다.

```bash
# 프로젝트 루트 디렉터리에서 실행
bash scripts/run_rtsp_server.sh
```
* **동작 방식:** 시스템에 `mediamtx` 바이너리가 있으면 바로 실행하고, 없으면 Docker를 이용해 `bluenviron/mediamtx:1` 이미지를 받아 띄웁니다.

---

### 3단계. Backend 실행 (Spring Boot Server)

백엔드 서버를 빌드하고 구동합니다.

```bash
# strange_back 디렉터리로 이동 후 실행
# macOS/Linux:
./gradlew bootRun

# Windows:
gradlew.bat bootRun
```
* **포트:** `8080`
* **Swagger 문서:** `http://localhost:8080/swagger-ui/index.html`

---

### 4단계. Frontend 실행 (React Dev Server)

웹 대시보드 화면을 구동합니다.

```bash
# strange_front 디렉터리로 이동 후 실행
npm install
npm run dev
```
* **포트:** `5173` (브라우저에서 `http://localhost:5173` 접속)

---

### 5단계. AI 추론 및 송출 실행

실제 카메라나 영상 파일이 준비되었는지에 따라 세 가지 모드로 실행할 수 있습니다.

#### A. 완전 Mock 시뮬레이터 실행 (RTSP 및 AI 하드웨어 없이 개발할 때)
RTSP 중계 서버나 PyTorch 디바이스가 없는 가벼운 프론트엔드/백엔드 화면 개발 연동 시 유용합니다.
```bash
# 프로젝트 루트 디렉터리에서 실행
python mock_edge_ai.py
```

#### B. 로컬 비디오 파일을 활용한 RTSP 시뮬레이션 및 AI 추론 실행
데모 영상 파일 풀을 활용해 MediaMTX에 RTSP 스트림을 스폰하고 AI 추론 분석 루프를 연동하는 시뮬레이션입니다.
```bash
# 1. 로컬 영상 파일을 MediaMTX RTSP 스트림으로 송출 시작
# GPU 리소스 고갈 및 인코딩 부하 방지를 위해 --ffmpeg-mode copy 모드를 강력히 권장합니다.
python scripts/start_simulated_rtsp_from_folder.py \
  --video-dir /home/welabs/yolo_training/ai_fall_experiments/data/raw/indoor_chromakey/videos \
  --backend-url http://localhost:8080 \
  --rtsp-host 127.0.0.1 \
  --rtsp-port 8554 \
  --poll-interval 30 \
  --ffmpeg-mode copy

# 2. 송출된 RTSP 스트림을 대상으로 YOLO + LSTM AI 분석 실행
# --overlay-base-port 8010을 주면, AI worker가 동작 순서에 맞게 포트를 동적으로 할당(8010, 8011...)합니다.
python scripts/run_registered_cameras.py \
  --backend-base-url "http://127.0.0.1:8080" \
  --rtsp-base-url "rtsp://127.0.0.1:8554" \
  --video-pool /home/welabs/yolo_training/ai_fall_experiments/data/raw/indoor_chromakey/videos \
  --overlay-base-port 8010 \
  --detector-mode real \
  --yolo-model yolo26n-pose.pt \
  --publisher mqtt \
  --mqtt-host "15.165.248.37" \
  --mqtt-port 1883 \
  --mqtt-topic "safety/events" \
  --skip-simulated-ffmpeg
```
> [!NOTE]
> * **로컬 vs 외부 MQTT:** 로컬 Mosquitto 테스트 시에는 `--mqtt-host 127.0.0.1`을 사용하며, 원격/외부 공용 이벤트 검증 시에는 `--mqtt-host 15.165.248.37`을 구분해 지정해야 합니다.


#### C. 단일 RTSP 카메라 스트림 분석기 직접 실행
```bash
python scripts/run_rtsp_inference.py \
  --rtsp-url rtsp://localhost:8554/cam1 \
  --camera-id cam_01 \
  --detector-mode real \
  --yolo-model yolo26n-pose.pt \
  --action-model benchmark/results/lstm_final_11n_vs_26n_audit/YOLO26n-pose/best.pt \
  --action-threshold 0.3 \
  --publisher mqtt
```

---

## ⏹️ 종료 순서 (Shutdown Workflow)

개발 또는 테스트를 마친 뒤 프로세스를 안전하게 정리하는 방법입니다.

1. **AI 추론 프로세스 종료:** 실행 중인 터미널에서 `Ctrl + C`를 눌러 파이썬 스크립트를 정지시킵니다.
2. **SSH 터널링 종료:** 로컬과 GPU PC 간 SSH 터널링을 구동 중이었다면 해당 터미널을 종료합니다.
3. **프론트엔드 및 백엔드 종료:** 각각의 터미널 환경에서 `Ctrl + C`로 `npm` 및 `gradle bootRun`을 종료합니다.
4. **인프라 정지:** Docker 컨테이너를 중지시킵니다.
   ```bash
   docker compose -f infra/local-mqtt/docker-compose.yml down
   ```
