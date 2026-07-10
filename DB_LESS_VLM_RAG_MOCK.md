# DB-less VLM/RAG Mock MVP

## Repository Audit

- Guidance files: only `front/README.md` exists in this workspace. No root `AGENTS.md` or frontend `DESIGN.md` was found.
- Project paths: `ai` is the Python edge worker, `back` is the Spring Boot backend, `front` is the Vite/React dashboard, and `infra` contains deployment support.
- Existing event contract: `back/src/main/java/com/strange/safety/event/SafetyEventDto.java` accepts `eventId`, `cameraLoginId`, `cameraId`, `trackId`, `clipPath`, and `clipUrl`.
- Existing alert persistence: `AlertEvent` stores a unique `event_id`, camera relation, severity/status, clip fields, bounding box data, and detected time.
- Existing statuses/events: repository code already references `Faint`, `Fall`, and VLM status enums. The mock contract defines `NEW_FALL`, `NEW_FAINT`, `FAINT_SUSPECTED`, `FALL_UNRECOVERED`, and `RECOVERED`. `NEW_FAINT` is reserved for compatibility; the checked-in fixtures use `NEW_FALL -> FAINT_SUSPECTED|FALL_UNRECOVERED -> RECOVERED`.
- Existing S3 path: `S3Service` uploads snapshots as `snapshots/{eventId}.jpg` and can issue presigned GET/PUT URLs. The DB-less mock does not call it.
- Migration tooling: no Flyway or Liquibase directory was found. The backend currently uses Spring JPA with `ddl-auto` from `application.yml`.
- Existing VLM/search code: `AlertEventDescription`, `VlmProcessingScheduler`, `EmbeddingService`, and `SemanticSearchService` are JPA/S3-backed. The new mock slice lives under `com.strange.safety.vlm.mock`.
- Current commands: backend uses Gradle, AI tests are Python `unittest`, frontend uses `npm run dev/build/lint` from `front/package.json`.

## DB-less Runtime Choice

The backend mock uses pure Java unit/demo execution. It does not start Spring Boot and does not initialize `DataSource`, JPA, Redis, S3, MQTT, or Gemini. This keeps the production defaults unchanged while giving future adapters a narrow target:

- `IncidentRepository`
- `VlmJobRepository`
- `VlmDescriptionRepository`
- `EmbeddingRepository`

The current implementations are `InMemoryIncidentRepository`, `InMemoryVlmJobRepository`, `InMemoryVlmDescriptionRepository`, and `MockEmbeddingRepository`.

## Incident/Event Contract

Incident identity is:

```text
cameraLoginId + originalEventId
```

All events sharing that identity belong to one `Incident`. Search results are returned at incident level, so repeated event rows do not duplicate one incident in the response.

The sample timeline is:

```text
NEW_FALL -> FAINT_SUSPECTED or FALL_UNRECOVERED -> RECOVERED
```

`NEW_FAINT` remains in the enum as an adapter target for future upstream event producers, but it is not emitted by the checked-in fixtures.

## Mock VLM

`MockVlmClient` emits stable `incident-v1` structured output. Timeline rules:

- `RECOVERED` makes `recoveryObserved=true`.
- `FAINT_SUSPECTED` makes `movementAfterEvent=low`.
- `FALL_UNRECOVERED` makes `recoveryObserved=false` and `riskLevel=CRITICAL`.

The Python worker mirrors this contract in `ai/scripts/process_vlm_mock.py` and prints only JSON to stdout. Logs go to stderr.

## Mock Embedding

`MockEmbeddingRepository` creates deterministic 768-dimensional vectors with SHA-256 token hashing. The same text always produces the same vector across processes. This is suitable for connection tests and metadata filter verification only. It is not a semantic-quality claim.

## Search Flow

```text
Sample Incident
-> Mock Media Asset
-> Mock VLM Job
-> Mock VLM structured JSON
-> IncidentSearchDocumentBuilder
-> deterministic 768-d embedding
-> in-memory repository save
-> semantic-like cosine search
-> incident-level SearchResult
```

Supported filters: `query`, `cameraLoginIds`, `eventTypes`, `incidentStatuses`, `from`, `to`, `topK`, and `minSimilarity`.

The Spring mock endpoint is:

```text
GET /api/mock/vlm/search?query=fall&cameraLoginIds=cam_02&eventTypes=NEW_FALL&incidentStatuses=RECOVERED,OPEN
```

It returns the same frontend response shape as the production semantic search panel without touching JPA, S3, Gemini, or real CCTV media. Production search endpoints remain unchanged and continue to use the existing JPA-backed service.

The frontend can use the checked-in fixture instead of the live semantic endpoint by starting Vite with:

```powershell
$env:VITE_VLM_MOCK_SEARCH = 'true'
npm run dev
```

When this flag is absent, the dashboard keeps calling the existing backend semantic search API.

## Fixtures

- Backend fixture source: `back/src/main/java/com/strange/safety/vlm/mock/MockIncidentFixture.java`
- AI worker job fixture: `ai/fixtures/vlm/demo_job.json`
- Frontend-ready fixture: `front/src/features/dashboard/data/mockVlmIncidents.json`

The backend fixture creates four incidents: fall followed by recovery, fall with low movement for more than 10 seconds, fall without recovery, and a normal/excluded event.

## Verified Commands

PowerShell:

```powershell
cd C:\Users\user\.gemini\antigravity\scratch\이상행동\ai
python scripts\process_vlm_mock.py --job fixtures\vlm\demo_job.json
python -m unittest tests.test_vlm_mock_worker

cd C:\Users\user\.gemini\antigravity\scratch\이상행동\back
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat runMockVlmRagTests assemble
.\gradlew.bat runMockVlmRagDemo

cd C:\Users\user\.gemini\antigravity\scratch\이상행동\front
npm run build

cd C:\Users\user\.gemini\antigravity\scratch\이상행동
git -C ai diff --check
git -C back diff --check
git -C front diff --check
```

The repository keeps Gradle `test.enabled=false` for the normal `test` task, so this MVP adds `runMockVlmRagTests` as a narrow programmatic JUnit runner. The full `build` target is still coupled to the existing `runAllTests` task; use `assemble` for the backend artifact check unless you are intentionally running the whole legacy test suite.

## Replacement Points For Real Infrastructure

- DB/JPA: implement `IncidentRepository`, `VlmJobRepository`, and `VlmDescriptionRepository` with JPA adapters.
- pgvector: replace `MockEmbeddingRepository` storage/ranking with a vector column and ANN index.
- S3: replace `MediaAsset.sourceKey` mock strings with `S3Service` upload/presigned URL adapters.
- Gemini/VLM: replace `MockVlmClient` with a provider implementation returning `VlmAnalysisResult`.
- Python worker opt-in: keep `application.yml` production default intact; for an isolated non-production run, override `VLM_PROCESS_SCRIPT=../ai/scripts/process_vlm_mock.py`.
- GPU PC validation: run the Python worker against extracted keyframe metadata first, then wire real video/keyframes after GPU access exists.

Out of scope for this MVP: real DB migrations, real pgvector search, S3 upload, presigned URL issuance, Gemini calls, API keys, real CCTV media, and semantic-quality evaluation.
