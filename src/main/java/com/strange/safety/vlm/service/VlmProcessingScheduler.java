package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.service.S3Service;
import com.strange.safety.vlm.dto.VlmIndexPayload;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.client.AiVlmWorkerClient;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class VlmProcessingScheduler {

    private static final int KEYFRAME_COUNT = 8;

    private final AlertEventDescriptionRepository repository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final VlmIndexPayloadParser payloadParser;
    private final VlmClipJobClaimService clipJobClaimService;
    private final VlmClipJobCompletionService clipJobCompletionService;

    private final AiVlmWorkerClient aiVlmWorkerClient;
    @Value("${vlm.mock-mode:${VLM_MOCK_MODE:true}}")
    private boolean mockMode;
    @Value("${vlm.gemini-api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${VLM_FORCE_MOCK:false}")
    private boolean forceMock;
    @Value("${vlm.python-executable:${VLM_PYTHON_EXECUTABLE:python}}")
    private String pythonExecutable;

    @Value("${vlm.process-script:${VLM_PROCESS_SCRIPT:}}")
    private String processScript;

    @Value("${vlm.batch-size:2}")
    private int batchSize;

    @Value("${vlm.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${vlm.max-stdout-bytes:1048576}")
    private int maxStdoutBytes;

    @Value("${vlm.capture-zone-id:UTC}")
    private String captureZoneId;

    @Value("${vlm.clip-start-sec:}")
    private String configuredClipStartSec;

    @Value("${vlm.clip-end-sec:}")
    private String configuredClipEndSec;

    @PostConstruct
    void applyProviderMode() {
        if (!forceMock && geminiApiKey != null && !geminiApiKey.isBlank()) {
            mockMode = false;
        }
        log.info("[VLM] scheduler mode={} aiWorkerConfigured={}",
                mockMode ? "mock" : "real",
                aiVlmWorkerClient.isConfigured());
    }

    @Scheduled(fixedDelayString = "${vlm.scheduler-delay-ms:15000}")
    public void processPendingJobs() {
        if (!aiVlmWorkerClient.isConfigured() && (processScript == null || processScript.isBlank())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> jobIds = clipJobClaimService.claimClipJobIds(now, batchSize);
        for (Long jobId : jobIds) {
            processClaimedJob(jobId);
        }
    }

    private void processClaimedJob(long jobId) {
        AlertEventDescription job = repository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        try {
            if (job.getSourceAssetType() != VlmSourceType.CLIP) {
                clipJobCompletionService.markFailed(jobId, "VLM queue accepts CLIP sources only");
                return;
            }
            RequestContext context = requestContext(job);
            VlmIndexPayload payload;
            if (mockMode) {
                payload = mockPayload(context);
            } else if (aiVlmWorkerClient.isConfigured()) {
                payload = invokeAiWorker(job, context);
            } else {
                payload = invokeProcess(job, context);
            }
            clipJobCompletionService.markSuccess(jobId, payload);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            clipJobCompletionService.markFailed(jobId, "VLM process was interrupted");
        } catch (Exception ex) {
            log.warn("[VLM] job failed jobId={} reason={}", jobId, safeFailure(ex));
            try {
                clipJobCompletionService.markFailed(jobId, safeFailure(ex));
            } catch (RuntimeException persistEx) {
                log.error("Failed to persist VLM failure for jobId={}: {}", jobId, persistEx.getMessage());
            }
        }
    }

    private VlmIndexPayload invokeProcess(AlertEventDescription job, RequestContext context)
            throws IOException, InterruptedException, ExecutionException {
        String inputUrl = s3Service.generatePresignedUrl(job.getSourceAssetKey());
        List<String> command = List.of(
                pythonExecutable,
                processScript,
                "--input-url", inputUrl,
                "--metadata", metadataJson(job, context),
                "--output-mode", "index"
        );
        Process process = new ProcessBuilder(command).start();
        CompletableFuture<BoundedOutput> stdoutFuture = readBounded(process.getInputStream(), maxStdoutBytes);
        CompletableFuture<BoundedOutput> stderrFuture = readBounded(process.getErrorStream(), 16_384);
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IOException("VLM process timed out after " + timeoutSeconds + " seconds");
        }
        BoundedOutput stdout = stdoutFuture.get();
        stderrFuture.get();
        if (process.exitValue() != 0) {
            throw new IOException("VLM process exited with code " + process.exitValue());
        }
        if (stdout.oversized()) {
            throw new IOException("VLM stdout exceeded " + maxStdoutBytes + " bytes");
        }
        try {
            return payloadParser.parseAndValidate(stdout.text(), context.expected(mockMode));
        } catch (RuntimeException | IOException ex) {
            throw new IOException("VLM process returned an invalid index payload", ex);
        }
    }

    private VlmIndexPayload invokeAiWorker(AlertEventDescription job, RequestContext context)
            throws AiVlmWorkerClient.AiVlmWorkerException {
        String inputUrl = s3Service.generatePresignedUrl(job.getSourceAssetKey());
        AlertEvent event = job.getAlertEvent();
        String scenario = event.getScenario() != null
                ? event.getScenario().getScenarioType().name()
                : "UNKNOWN";
        String severity = event.getSeverity() != null ? event.getSeverity().name() : "WARNING";
        return aiVlmWorkerClient.processClipJob(
                job.getId(),
                context.incidentId(),
                context.cameraLoginId(),
                inputUrl,
                scenario,
                severity,
                context.capturedAt().toString(),
                context.clipStartSec(),
                context.clipEndSec()
        );
    }

    private RequestContext requestContext(AlertEventDescription job) {
        AlertEvent event = job.getAlertEvent();
        String incidentId = event.getEventId();
        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalStateException("Alert event has no authoritative incident ID");
        }
        String cameraLoginId = event.getCamera() != null
                ? event.getCamera().getCameraLoginId()
                : event.getCorporateCamera().getCameraLoginId();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(captureZoneId);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("VLM capture zone ID is invalid");
        }
        OffsetDateTime capturedAt = event.getDetectedAt().atZone(zoneId).toOffsetDateTime();
        double clipStartSec = configuredClipStartSec(configuredClipStartSec);
        Double clipEndSec = configuredClipEndSec(configuredClipEndSec);
        if (clipStartSec < 0 || (clipEndSec != null && clipEndSec <= clipStartSec)) {
            throw new IllegalStateException("Configured VLM clip bounds are invalid");
        }
        return new RequestContext(incidentId, cameraLoginId, capturedAt, clipStartSec, clipEndSec);
    }

    private double configuredClipStartSec(String configured) {
        if (configured == null || configured.isBlank()) {
            return 0.0;
        }
        return parseFiniteBound(configured, "VLM_CLIP_START_SEC");
    }

    private Double configuredClipEndSec(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return parseFiniteBound(configured, "VLM_CLIP_END_SEC");
    }

    private double parseFiniteBound(String configured, String name) {
        try {
            double value = Double.parseDouble(configured);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(name + " must be finite");
        }
    }

    private String metadataJson(AlertEventDescription job, RequestContext context) throws IOException {
        AlertEvent event = job.getAlertEvent();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("incident_id", context.incidentId());
        metadata.put("camera_login_id", context.cameraLoginId());
        metadata.put("captured_at", context.capturedAt().toString());
        metadata.put("clip_start_sec", context.clipStartSec());
        if (context.clipEndSec() != null) {
            metadata.put("clip_end_sec", context.clipEndSec());
        }
        metadata.put("event_type", "safety_event");
        metadata.put("severity", event.getSeverity().name());
        metadata.put("scenario_type", event.getScenario().getScenarioType().name());
        return objectMapper.writeValueAsString(metadata);
    }

    private VlmIndexPayload mockPayload(RequestContext context) throws IOException {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schema_version", "vlm-result-v1");
        result.put("incident_id", context.incidentId());
        result.put("visual_event_type", "person_lying_on_floor");
        result.put("people_count", 1);
        ArrayNode resultKeywords = result.putArray("korean_search_keywords");
        List.of("작업자", "쓰러짐", "바닥", "안전모", "복도").forEach(resultKeywords::add);
        result.put("detailed_description_ko", "작업자 또는 사람이 감시 구역 바닥 근처에 있는 안전 이벤트로 보입니다.");
        result.put("frame_count", KEYFRAME_COUNT);
        result.put("provider", "mock");
        result.put("is_mock", true);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schema_version", VlmIndexPayloadParser.SCHEMA_VERSION);
        root.put("incident_id", context.incidentId());
        root.put("camera_login_id", context.cameraLoginId());
        root.put("captured_at", context.capturedAt().toString());
        root.set("vlm_result", result);
        ObjectNode search = root.putObject("search");
        search.put("document", "Mock VLM safety event: 작업자 쓰러짐 바닥 안전모 복도");
        ArrayNode keywords = search.putArray("keywords");
        List.of("작업자", "쓰러짐", "바닥", "안전모", "복도").forEach(keywords::add);
        search.put("embedding_model", "mock-vlm-index-768");
        search.put("embedding_dimension", VlmIndexPayloadParser.EMBEDDING_DIMENSION);
        ArrayNode embedding = search.putArray("embedding");
        for (int index = 0; index < VlmIndexPayloadParser.EMBEDDING_DIMENSION; index += 1) {
            embedding.add(index == 0 ? 1.0d : 0.0d);
        }
        return payloadParser.parseAndValidate(objectMapper.writeValueAsString(root), context.expected(true));
    }



    private CompletableFuture<BoundedOutput> readBounded(InputStream input, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (input; ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192))) {
                byte[] buffer = new byte[8192];
                int total = 0;
                boolean oversized = false;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    int writable = Math.min(read, Math.max(0, limit - total));
                    if (writable > 0) {
                        output.write(buffer, 0, writable);
                    }
                    total += read;
                    oversized |= total > limit;
                }
                return new BoundedOutput(output.toString(StandardCharsets.UTF_8), oversized);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read VLM process output", ex);
            }
        });
    }
    private String safeFailure(Exception ex) {
        if (ex instanceof AiVlmWorkerClient.AiVlmWorkerException
                || ex instanceof IllegalStateException
                || ex instanceof IllegalArgumentException) {
            return ex.getMessage();
        }
        if (ex.getMessage() != null && (ex.getMessage().startsWith("VLM process timed out")
                || ex.getMessage().startsWith("VLM process exited")
                || ex.getMessage().startsWith("VLM stdout exceeded"))) {
            return ex.getMessage();
        }
        return "VLM indexing failed validation or processing";
    }

    private record RequestContext(String incidentId, String cameraLoginId, OffsetDateTime capturedAt,
                                  double clipStartSec, Double clipEndSec) {
        VlmIndexPayloadParser.Expected expected(boolean mock) {
            return new VlmIndexPayloadParser.Expected(incidentId, cameraLoginId, capturedAt, mock);
        }
    }

    private record BoundedOutput(String text, boolean oversized) {
    }
}
