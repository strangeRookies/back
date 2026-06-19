# 트러블슈팅 가이드 (TROUBLESHOOTING)

본 문서는 스마트 안전 관제 시스템 운영 및 로컬 개발 과정에서 마주칠 수 있는 빈번한 장애 상황과 원인, 확인 방법 및 해결 단계를 안내합니다.

---

## HLS `index.m3u8` 404 에러

### 증상
- 프론트엔드 CCTV 격자 화면이나 비디오 재생 컴포넌트에서 비디오가 로딩 상태에서 멈추고, 브라우저 개발자 도구 네트워크 탭에 `http://localhost:8888/{cameraLoginId}/index.m3u8 404 Not Found` 에러가 기록됩니다.

### 원인 후보
1. MediaMTX 서버가 미작동 중이거나 RTSP 소스가 MediaMTX로 들어오지 않는 경우.
2. AI 시뮬레이터(RTSP Publisher) 또는 실제 CCTV 카메라의 RTSP 피드가 끊긴 경우.
3. 등록된 카메라의 `cameraLoginId`와 RTSP 송출 상의 패스명이 불일치하는 경우.

### 확인 방법
1. 브라우저에서 `http://localhost:8888` 혹은 MediaMTX 대시보드에 접근하여 해당 스트림이 살아있는지 확인합니다.
2. 아래 curl 명령어로 직접 호출하여 응답을 검증합니다.
   ```bash
   curl -L http://127.0.0.1:8888/cam_01/index.m3u8
   ```
   `#EXTM3U` 텍스트로 시작하는 헤더가 아닌 HTML 404 에러가 출력되면 스트림 부재 상태입니다.

### 해결 방법
1. MediaMTX 서버를 실행합니다 (`bash scripts/run_rtsp_server.sh`).
2. RTSP 소스를 재송출합니다 (`python scripts/start_simulated_rtsp_from_folder.py` 또는 실제 카메라의 전원과 네트워크선을 확인).
3. 백엔드/프론트엔드 설정의 `cameraLoginId`가 실제 RTSP 스트림의 URI 세그먼트와 완전히 대소문자까지 일치하는지 확인하십시오 (예: `cam_01` vs `cam01`).

---

## MQTT 연결 실패

### 증상
- AI 추론기 실행 시 `Connection refused` 로그가 발생하고 중단되거나, 백엔드 서버 기동 시 `MqttException: Broker not available` 등의 에러 메시지와 함께 실행이 차단됩니다.

### 원인 후보
1. MQTT Broker(EMQX/Mosquitto) 컨테이너가 내려가 있는 경우.
2. `.env` 또는 `application.yml`에 설정된 `MQTT_HOST`, `MQTT_PORT` 정보가 잘못 기입된 경우.
3. 방화벽이나 클라우드 보안 그룹(Security Group) 설정으로 인해 1883 포트가 차단된 경우.

### 확인 방법
1. Docker 컨테이너 목록을 통해 MQTT 브로커 상태를 확인합니다.
   ```bash
   docker ps | grep mqtt
   ```
2. `telnet` 또는 `nc` 명령어로 포트 오픈 상태를 조회합니다.
   ```bash
   telnet localhost 1883
   ```

### 해결 방법
1. 로컬 도커 환경의 경우 브로커 컨테이너를 올립니다.
   ```bash
   docker compose -f infra/local-mqtt/docker-compose.yml up -d
   ```
2. 연결 실패를 겪는 모듈의 설정 파일(`application.yml` 또는 `.env`)에서 `MQTT_HOST` 주소가 `localhost` 혹은 실제 서버 IP로 제대로 명시되었는지 점검합니다.

---

## RTSP 연결 실패

### 증상
- AI 추론 엔진 실행 시 `[ERROR] Cannot open RTSP Stream: rtsp://...` 경고가 주기적으로 출력되며 영상 프레임을 읽어오지 못합니다.

### 원인 후보
1. 카메라의 물리적 전원이 꺼졌거나 IP 주소가 변경된 경우.
2. RTSP 주소 내 계정 정보(ID/Password) 인증 오류.
3. MediaMTX가 동작하지 않아 루프백 RTSP(`rtsp://localhost:8554`) 송신을 받을 수 없는 경우.

### 확인 방법
1. 로컬 환경의 경우 `VLC`나 `ffplay` 미디어 플레이어 프로그램으로 해당 RTSP URL을 열어 영상이 열리는지 테스트합니다.
   ```bash
   ffplay rtsp://localhost:8554/cam_01
   ```

### 해결 방법
1. 카메라 기기의 고정 IP 상태와 전원 연결을 복구합니다.
2. MediaMTX 미디어 서버를 먼저 가동합니다.
3. RTSP 연결 문자열의 유효성을 체크하고 필요 시 ID/비밀번호 설정을 일치시킵니다.

---

## 카메라 등록은 됐는데 화면이 안 뜨는 경우 (WebRTC/WHEP 404 & HLS Fallback 실패)

### 증상
- 백엔드에 카메라는 4대 정상 등록되어 있으나, 메인 대시보드 그리드 칸이 완전히 검은색 화면이며 **"연결 없음 (카메라 연결 상태를 확인해 주세요)"** 오버레이가 씌워집니다.
- 브라우저 개발자 도구 F12 콘솔 창에 `SDP / WHEP negotiation failed: WHEP POST request failed (HTTP 404)` 에러가 다량 발생하고, HLS 폴백 링크마저도 `404 Not Found`가 발생합니다.

### 원인 후보
1. **송출기(RTSP Publisher) 프로세스 중단 (가장 유력):**
   * 미디어 서버(MediaMTX) 포트 `8889`가 살아 있어 프론트엔드가 접속을 시도했으나, 해당 스트림 경로(`cam_01` 등)로 원본 영상을 발행해 주는 파이썬 송출기(`start_simulated_rtsp_from_folder.py`) 프로세스가 중단되었거나, 인코더 에러(NVENC 세션 한계 등)로 즉사한 경우입니다. 스트림이 공급되지 않으면 MediaMTX WHEP 경로는 404 에러를 뱉습니다.
2. **역방향 포트 포워딩(`-R 8080`) 차단 및 로컬 백엔드 미동작:**
   * GPU PC에서 기동된 송출기가 로컬 백엔드(`http://localhost:8080`)에 카메라 목록을 조회하려다 접속에 실패하여 에러를 내고 즉시 크래시된 경우입니다.
3. **정적 대시보드 상태 갱신 누락:**
   * 백엔드 상에서 카메라 상태가 `CONNECTED`로 복구되었음에도, 프론트 대시보드 오버레이와 하단 뱃지가 정적 상태 캐시(`liveCameras.connectionStatus`)를 사용하여 실시간으로 연동 갱신되지 못하고 여전히 검은 마스크를 유지하는 경우입니다.
4. **비정규화 `cameraLoginId` 경로 사용:**
   * `cam1` ~ `cam4` 같은 구버전 명칭을 사용해 경로 매핑이 어긋난 경우입니다.

### 확인 방법
1. 로컬 윈도우에서 실제 스트림 경로를 직접 `curl`로 찔러 봅니다:
   ```bash
   curl.exe -I http://localhost:8888/cam_01/index.m3u8
   ```
   * `404 Not Found`가 반환된다면 미디어 서버 포트는 정상이지만 원격 송출기가 죽어 스트림이 없는 상태입니다.
2. 브라우저 콘솔에서 `SDP / WHEP negotiation failed` 로그의 상세 Reason 코드를 점검합니다.

### 해결 방법
1. GPU PC에서 찌꺼기 프로세스들을 완전히 정리한 후, **FFmpeg 인코딩 연산 부하가 없는 copy 모드**로 송출기를 재기동합니다:
   ```bash
   # GPU PC SSH 터미널에서 실행
   pkill -f "start_simulated_rtsp_from_folder.py" || true
   nohup python scripts/start_simulated_rtsp_from_folder.py \
     --video-dir /home/welabs/yolo_training/ai_fall_experiments/data/raw/indoor_chromakey/videos \
     --backend-url http://localhost:8080 \
     --rtsp-host 127.0.0.1 \
     --rtsp-port 8554 \
     --poll-interval 30 \
     --ffmpeg-mode copy > publisher.log 2>&1 &
   ```
2. 카메라 정보의 `cameraLoginId`가 2자리 패딩 규칙(`cam_01`, `cam_02` 등)을 철저히 지켜 등록되었는지 확인합니다.
3. 송출기 기동에 성공했다면, 대시보드 웹 브라우저를 **새로고침(F5)**하여 캐시된 카메라 상태를 최신화시킵니다.


---

## AI 서버에서 person detection이 안 되는 경우

### 증상
- AI 프로세스 로그에 프레임 처리 로그는 발생하지만 `persons_detected=0` 또는 0에 가까운 값이 연속되어 객체 감지 박스 및 골격 라인이 보이지 않습니다.

### 원인 후보
1. YOLO 모델 파일(`.pt`)의 로드에 실패하여 추론기가 구동되지 못한 경우.
2. 프레임 캡처 이미지 해상도가 깨지거나 손상되어 들어오는 경우.
3. 감지 신뢰도 문턱 값(`--detector-conf` 등)이 지나치게 높게 설정된 경우.

### 확인 방법
1. AI 실행 스크립트 출력창의 YOLO 가중치 로드 성공 여부(`yolo26n-pose.pt` loaded...)를 진단합니다.
2. 분석기 실행 인자 중 `--detector-conf`가 높은 값(예: 0.8 이상)으로 주입되었는지 살핍니다.

### 해결 방법
1. `--yolo-model yolo26n-pose.pt` 가중치 파일이 정상 배치되었는지 점검합니다 (인터넷 연결이 안 되는 GPU PC 환경이라면 미리 수동 다운로드 후 파일 경로 주입).
2. 감지 임계값을 기본 사양(`0.10`~`0.25`) 수준으로 하향 조정합니다.

---

## 이벤트가 발생했는데 프론트 알림이 안 뜨는 경우

### 증상
- AI 콘솔 창에 Faint 상황이 탐지되어 MQTT 메시지가 성공적으로 Publish 되었으나, 웹 브라우저 관리 화면에는 사이렌음이나 경고 카드가 활성화되지 않습니다.

### 원인 후보
1. 백엔드 서버의 MQTT 구독 파트가 비활성화되었거나 에러가 난 경우.
2. WebSocket STOMP 연결 끊김 혹은 목적지(`destination: /topic/alerts`) 바인딩 오류.
3. 프론트엔드가 STOMP 메시지 수신 후 fingerprint 캐시 필터에 의해 동일 이벤트로 인지되어 중복 무시 처리된 경우.

### 확인 방법
1. 백엔드 구동 콘솔에서 `Saved MQTT safety alert event` 로그가 출력되는지 살핍니다.
2. 프론트엔드 브라우저 콘솔에서 `[useAiEvents] Received STOMP payload:` 메시지가 출력되는지 모니터링합니다.

### 해결 방법
1. 백엔드의 application.yml 설정을 열어 `mqtt.topic` 값이 `safety/events`인지 점검합니다.
2. 프론트엔드의 커스텀 STOMP 수신부의 자동 재연결 기능이 잘 유지되는지 검사하고 브라우저 페이지를 리프레시합니다.

---

## CORS 문제

### 증상
- 프론트엔드 웹 브라우저 콘솔에 `Access to XMLHttpRequest at 'http://localhost:8080/api/...' from origin 'http://localhost:5173' has been blocked by CORS policy` 에러가 발생하며 API 요청이 차단됩니다.

### 원인 후보
1. 백엔드 서버의 CORS 설정이 누락되었거나 허용 원본(Allowed Origin)에 `http://localhost:5173`가 포함되지 않은 경우.
2. 프론트엔드 주소가 로컬 IP 주소(`127.0.0.1` 등)로 호출되면서 도메인 불일치가 일어난 경우.

### 확인 방법
1. 브라우저 F12의 Network 탭에서 실패한 API 호출의 Response Header에 `Access-Control-Allow-Origin` 헤더 유무를 대조합니다.

### 해결 방법
1. 백엔드 `application.yml`의 `app.websocket.allowed-origin-patterns` 값 및 컨트롤러들의 `@CrossOrigin` 설정에 프론트 개발 포트(`http://localhost:5173`, `http://localhost:3000`)를 직접 지정합니다.

---

## 포트 충돌 문제

### 증상
- 서버 구동 시 `java.net.BindException: Address already in use` 또는 `OSError: [Errno 98] Address already in use` 에러가 나면서 백엔드나 AI 스크립트 프로세스가 즉사합니다.

### 원인 후보
1. 백엔드(8080), MediaMTX(8554, 8888), MQTT(1883) 또는 AI Overlay(8010~8013) 포트를 이전 프로세스가 여전히 점유하고 있는 경우.

### 확인 방법
1. 터미널 명령어를 통해 충돌 포트를 검사합니다.
   * **Windows:** `netstat -ano | findstr 8080`
   * **Linux:** `sudo lsof -i :8080` 또는 `fuser 8080/tcp`

### 해결 방법
1. 포트를 물고 있는 기존 프로세스를 파괴합니다.
   * **Linux:** `fuser -k 8080/tcp`
   * **Windows:** `taskkill /F /PID <조회된PID>`
2. 배치 파일(`AI_실행_딸깍.bat`) 상단에 명시된 프로세스 킬 스크립트를 수동 구동시킵니다.

---

## 모델 파일 경로 오류

### 증상
- AI 추론기 시작 즉시 `FileNotFoundError`가 터지며 프로세스가 강제 정지됩니다.

### 원인 후보
1. `--action-model` 경로에 명시된 PyTorch LSTM 가중치 파일(`best.pt`)이 존재하지 않는 경우.
2. 작업 실행 디렉터리(`CWD`)가 프로젝트 루트가 아닌 상태에서 상대 경로로 실행한 경우.

### 확인 방법
1. `ls` 명령어로 해당 모델 경로에 파일이 실제로 상주하는지 체크합니다.
2. Python 터미널을 열어 `os.path.exists('benchmark/results/...')`를 수행해 봅니다.

### 해결 방법
1. 모델 디렉터리가 생성되어 있고 파일명이 맞는지 확인합니다.
2. 스크립트 실행 경로를 쉘에서 작업 경로 기준으로 바로잡거나(기본 `/home/welabs/yolo_training/strange_ai_lstm`), 절대 경로 방식으로 `--action-model` 인자를 제공하십시오.

---

## DB 연결 실패

### 증상
- Spring Boot 백엔드 서버 구동 시 `HikariPool-1 - Connection is not available, request timed out` 메시지가 뜨면서 스프링 부트 로드에 완전히 실패합니다.

### 원인 후보
1. PostgreSQL 데이터베이스 인스턴스가 동작하지 않고 있는 경우.
2. `application.yml` 또는 `.env` 설정에 지정된 JDBC 접속 정보(`DB_URL`), 계정 정보(`DB_USERNAME` / `DB_PASSWORD`)가 상이한 경우.

### 확인 방법
1. 데이터베이스 서비스 포트 접속 가능 여부를 dbeaver 같은 클라이언트로 직접 붙어 확인합니다.
2. Docker 환경이라면 `docker ps`를 통해 PostgreSQL 컨테이너 상태를 탐색합니다.

### 해결 방법
1. 로컬 PostgreSQL 컨테이너가 돌고 있지 않다면 기동시킵니다.
2. `application.yml`의 `spring.datasource.url` 및 `.env` 파일의 패스워드 주입 상태를 실제 설정 정보와 일치시킵니다.
