# 실시간 스트리밍 지연(Latency) 및 프레임 측정/비교 가이드

본 문서는 HLS 모드와 MJPEG 모드 간의 전송 지연 시간(End-to-End Latency) 및 프레임 레이트(FPS)를 정량적으로 측정하고, 최적화하기 위해 검토해야 하는 주요 요소들을 정의합니다.

---

## 1. 지연 시간(Latency) 측정 방법

### 1.1. 타임코드 오버레이 방식 (가장 정확하고 직관적인 측정)
비디오 소스 프레임 자체에 밀리초(ms) 단위의 시스템 타임코드를 오버레이하여 출력한 뒤, 원본 타임코드와 브라우저 플레이어 화면의 타임코드를 비교합니다.

* **측정 방법:**
  1. 비디오 송출기(`ffmpeg`) 또는 AI 분석 엔진에서 프레임에 현재 시스템 시간(Epoch Time)을 그려 넣습니다.
     * **FFmpeg 예시 (drawtext 필터 사용):**
       `-vf "drawtext=fontfile=Arial.ttf:text='%{pts\:localtime\:1718512345\:%m-%d %H\\:%M\\:%S}.%{eif\:mod(t*1000,1000)\:d\:3}':x=10:y=10:fontsize=24:fontcolor=white"`
     * **OpenCV 예시 (Python):**
       `cv2.putText(frame, f"{time.time():.3f}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,0,255), 2)`
  2. 스마트폰 카메라 등으로 원본 소스를 띄워둔 모니터(또는 터미널 시간 출력)와 브라우저 수신 화면을 **한 화면에 동시에 사진 촬영(또는 스크린샷)** 합니다.
  3. `지연 시간 = (사진 촬영 순간의 원본 시간) - (사진 속 브라우저 화면의 타임코드)`

---

## 2. 스트리밍 성능 분석 시 확인해야 할 핵심 지표

### 2.1. 브라우저 개발자 도구 (Network 탭)
브라우저에서 `F12` 개발자 도구를 열고 **Network** 탭을 확인합니다.

1. **HLS (`index.m3u8`, `.ts`) 주기 확인:**
   * HLS는 비디오를 잘게 쪼갠 세그먼트(`.ts`) 단위로 HTTP 요청을 보냅니다.
   * 각 `.ts` 파일의 파일 크기(Size)와 전송 완료 시간(Time), 호출 주기(Interval)를 기록합니다.
   * **지연 원인:** 기본적으로 HLS 플레이어는 재생 안정성을 위해 플레이리스트 상의 세그먼트 3개 분량(예: 세그먼트가 2초이면 최소 6초)을 버퍼링한 후에 재생을 시작하므로 구조적 지연이 발생합니다.
2. **MJPEG (`multipart/x-mixed-replace`) 스트림 확인:**
   * MJPEG는 단일 HTTP 커넥션을 유지하며 이미지 데이터를 경계선(Boundary) 기준으로 계속 밀어주는 방식입니다.
   * 호출이 단 1회 일어나며 상태가 `Pending`으로 계속 유지됩니다.
   * **지연 원인:** MJPEG는 압축률이 HLS/H.264보다 매우 떨어지므로 해상도가 높을 경우 네트워크 대역폭(Throughput)이 부족하면 프레임 드랍이나 지연이 점진적으로 누적될 수 있습니다.

---

## 3. 지연 최소화(Low-Latency) 최적화 포인트

스트리밍 지연을 줄이기 위해 조정해야 하는 설정값들입니다.

### 3.1. FFmpeg 인코딩 설정 (키프레임 간격 - GOP)
HLS 세그먼트는 반드시 새로운 키프레임(I-Frame)으로 시작되어야 합니다. GOP(Group of Pictures) 크기가 너무 크면 세그먼트를 촘촘하게 쪼갤 수 없습니다.

* **인코더 튜닝 옵션 (FFmpeg):**
  * `-g 30` (30프레임마다 키프레임 생성, 30fps 기준 1초당 1개)
  * `-preset ultrafast -tune zerolatency` (인코딩 버퍼링 및 B-프레임 제거로 지연 최소화)

### 3.2. MediaMTX HLS 세그먼트 설정 (`mediamtx.yml`)
MediaMTX 설정 파일에서 HLS 생성 및 대기 규칙을 튜닝하여 버퍼링 길이를 크게 단축할 수 있습니다.

```yaml
# stream/mediamtx.yml 또는 설정 환경변수 튜닝 예시
hlsAlwaysShowPublishers: yes
hlsSegmentDuration: 1s      # 세그먼트 크기를 1초(또는 500ms)로 대폭 낮춤
hlsPartDuration: 200ms      # Low-Latency HLS(LL-HLS) 파트 크기 단축
hlsSegmentCount: 3          # 보관할 세그먼트 수 최소화
```

### 3.3. 프론트엔드 HLS 플레이어 (`hls.js`) 버퍼 튜닝
React 앱의 `hls.js` 라이브러리 초기화 옵션을 통해 수신 지연을 인위적으로 당길 수 있습니다.

```typescript
const hls = new Hls({
  liveSyncDurationCount: 1.5,       // 최신 세그먼트 경계선으로부터 1.5개 뒤에서 바로 재생 (지연 최소화)
  liveMaxLatencyDurationCount: 3,   // 최대 허용 지연 버퍼 제한
  maxLiveSyncPlaybackRate: 1.5,     // 라이브 지연이 늘어났을 때 배속 재생으로 라이브 지점 추적 기능 활성화
});
```

---

## 4. 최종 스트리밍 기술 비교표

| 비교 항목 | RTSP (Raw) | HLS (MediaMTX) | MJPEG (AI Overlay) |
| :--- | :--- | :--- | :--- |
| **평균 지연 시간** | `0.1s ~ 0.5s` (매우 낮음) | `2s ~ 10s` (높음, 버퍼링 의존) | `0.2s ~ 1.0s` (낮음) |
| **대역폭 소모량** | 낮음 (H.264 압축) | 낮음 (H.264 압축) | 매우 높음 (개별 JPEG 전송) |
| **브라우저 호환성** | 불가능 (플러그인 필요) | 우수 (`hls.js` 사용) | 매우 우수 (기본 `<img>` 지원) |
| **CPU/GPU 부하** | 낮음 | 중간 (세그먼트 분할 부하) | 높음 (개별 이미지 압축/드로잉) |
| **비교 목적** | AI 추론 원본 품질 기준점 | 웹 원본 모니터링 품질 비교 | AI 오버레이 시각화 실시간성 비교 |
