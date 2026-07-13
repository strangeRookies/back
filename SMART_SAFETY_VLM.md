# 스마트 안전관제 VLM 파이프라인

> 범위: **골격 구현 + 계약 정리**. 실 AWS DB / S3 / GPU / Gemini 키 연동 검증은 미실시.  
> 브랜치: `vlm-home` (ai / back / front)

## 결정 사항 (체크리스트)

| 항목 | 상태 | 구현 위치 |
|------|------|-----------|
| 이벤트 발생 → DB 저장 | ✅ | `AlertEvent` 저장 후 `VlmDescriptionEnqueueService.enqueueIfMediaExists` → `alert_event_descriptions` |
| VLM Worker | ✅ | Python `ai/scripts/process_vlm.py` (+ mock: `process_vlm_mock.py`) / 백엔드 `VlmProcessingScheduler` 호출 |
| Embedding Worker | ✅ | Java `EmbeddingWorker` + `EmbeddingClient` / Python `ai/scripts/process_embed.py` |
| pgvector | ✅ | 스키마 `src/main/resources/db/pgvector_alert_event_descriptions.sql` + `PgVectorSearchRepository` |
| SDK 직접 호출 | ✅ | `GeminiEmbeddingClient`, `ai/vlm_sdk.py`, `ai/embedding_sdk.py` — **HTTP/SDK only** |
| LangChain | ❌ | 의존성·코드 경로에 포함하지 않음 |

## 전체 흐름

```text
[AI Edge / MQTT SafetyEvent]
        │
        ▼
[Backend] AlertEvent 영속화 (이벤트 발생 → DB 저장)
        │
        ▼
[Backend] VlmDescriptionEnqueueService
        │  alert_event_descriptions (status=PENDING)
        ▼
[Backend] VlmProcessingScheduler  ──spawn──►  [Python] VLM Worker
        │                                      process_vlm.py
        │                                      (SDK 직접 호출, no LangChain)
        │  vlm_json + vlm_description
        ▼
[Backend] EmbeddingWorker / EmbeddingClient
        │  (mock | Gemini text-embedding SDK 직접 호출)
        │  description_embedding (+ optional vector column)
        ▼
[PostgreSQL + pgvector]
        │
        ▼
[Backend] SemanticSearchService / PgVectorSearchRepository
        │
        ▼
[Front] 대시보드 시맨틱 검색
```

## 레이어 역할

### 1) 이벤트 → DB
- 진입: 안전 이벤트 수신/저장 경로 (`AlertEventService` 등).
- 후처리: 미디어(소스 키)가 있으면 VLM 잡 1건 enqueue.
- 테이블: `alert_events`, `alert_event_descriptions`.

### 2) VLM Worker
- **계약**: stdin 아님. CLI 인자 + **stdout = JSON only**, 로그는 stderr.
- Mock: `VLM_MOCK_MODE=true` 또는 `process_vlm_mock.py` (incident-v1).
- Real (골격): `ai/vlm_sdk.py` 의 provider가 Gemini/기타 멀티모달 SDK를 **직접** 호출.
- 백엔드 호출: `VlmProcessingScheduler` → ProcessBuilder.

### 3) Embedding Worker
- VLM 결과 텍스트 → 벡터.
- Java: `EmbeddingClient` 구현체 선택 (`mock` | `gemini`).
- Python 보조 CLI: `process_embed.py` (동일 계약, 배치/GPU 쪽 이관용).
- **LangChain 미사용**.

### 4) pgvector
- 확장: `CREATE EXTENSION vector`.
- 컬럼: `description_embedding_vec vector(768)` (실서비스 차원; mock 키워드 벡터는 32-d 텍스트 컬럼 유지 가능).
- 검색: cosine / `<=>` 연산. 현재 기본 `SemanticSearchService`는 앱 메모리 cosine(텍스트 인코딩) 유지, pgvector 경로는 `PgVectorSearchRepository`로 교체 가능.

### 5) SDK 직접 호출 / LangChain 제외
- 허용: 공식 REST/SDK (`urllib`, `google-genai` 등) 직접 호출.
- 금지: `langchain*`, 체인/에이전트 프레임워크로 파이프라인 구성.

## 설정 키

```yaml
vlm:
  mock-mode: true          # 로컬 기본. 운영에서 false + 키 주입
  process-script: .../process_vlm.py
  embedding-provider: mock # mock | gemini
  embedding-dimension: 768 # pgvector 컬럼 기준
  gemini-api-key: ${GEMINI_API_KEY:}
```

## 로컬 검증 (실인프라 없이)

```powershell
# AI mock VLM
cd ai
python scripts\process_vlm_mock.py --job fixtures\vlm\demo_job.json
python scripts\process_embed.py --text "복도에서 쓰러진 사람"
python -m unittest tests.test_vlm_mock_worker tests.test_embedding_sdk

# Backend mock RAG (DB 없음)
cd back
.\gradlew.bat runMockVlmRagTests runMockVlmRagDemo

# Front mock search UI
cd front
$env:VITE_VLM_MOCK_SEARCH='true'
npm run dev
```

## 실연동 시 남은 작업

1. AWS RDS에 `pgvector` 확장 + SQL 적용  
2. `GEMINI_API_KEY` / 멀티모달 모델 설정, `vlm.mock-mode=false`  
3. S3 presigned GET/PUT 실미디어  
4. `SemanticSearchService.rank` → `PgVectorSearchRepository` 전환 플래그  
5. GPU PC에서 `process_vlm.py` 실추론 스모크  
6. 계정·facility·camera 시드 (빈 대시보드 해소)

## 관련 파일

| 구분 | 경로 |
|------|------|
| Enqueue | `vlm/service/VlmDescriptionEnqueueService.java` |
| VLM 스케줄러 | `vlm/service/VlmProcessingScheduler.java` |
| Embedding | `vlm/service/EmbeddingService.java`, `vlm/embedding/*` |
| pgvector | `src/main/resources/db/pgvector_alert_event_descriptions.sql`, `vlm/pgvector/*` |
| Mock MVP | `vlm/mock/*`, `DB_LESS_VLM_RAG_MOCK.md` |
| Python VLM | `ai/scripts/process_vlm.py`, `ai/vlm_sdk.py` |
| Python Embed | `ai/scripts/process_embed.py`, `ai/embedding_sdk.py` |
