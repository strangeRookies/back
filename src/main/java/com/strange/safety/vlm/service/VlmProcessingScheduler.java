package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.service.S3Service;
import com.strange.safety.vlm.embedding.PgVectorProjectionWriter;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draft ProcessBuilder-based VLM job runner. Does not mutate AlertEvent status.
 * Disabled entirely when {@code vlm.enabled=false} (default).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vlm.enabled", havingValue = "true")
public class VlmProcessingScheduler {
    private static final Logger log = LoggerFactory.getLogger(VlmProcessingScheduler.class);
    private static final int MAX_STREAM_BYTES = 256 * 1024;

    private final AlertEventDescriptionRepository repository;
    private final EmbeddingService embeddingService;
    private final PgVectorProjectionWriter pgVectorProjectionWriter;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    @Value("${vlm.mock-mode:${VLM_MOCK_MODE:true}}")
    private boolean mockMode;

    @Value("${vlm.python-executable:${VLM_PYTHON_EXECUTABLE:python}}")
    private String pythonExecutable;

    @Value("${vlm.process-script:${VLM_PROCESS_SCRIPT:../strange_ai/scripts/process_vlm.py}}")
    private String processScript;

    @Value("${vlm.batch-size:2}")
    private int batchSize;

    @Value("${vlm.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${vlm.daily-job-limit:10}")
    private int dailyJobLimit;

    @Value("${vlm.max-retry:1}")
    private int maxRetry;

    @Value("${vlm.process-existing-events:false}")
    private boolean processExistingEvents;

    private final AtomicInteger dailySuccessCount = new AtomicInteger(0);
    private LocalDate dailyCountDay = LocalDate.now();

    @Scheduled(fixedDelayString = "${vlm.scheduler-delay-ms:15000}")
    @Transactional
    public void processPendingJobs() {
        // Bean is only loaded when vlm.enabled=true; still guard daily limit.
        resetDailyCounterIfNeeded();
        if (dailySuccessCount.get() >= Math.max(0, dailyJobLimit)) {
            log.debug("[VLM] daily job limit reached ({})", dailyJobLimit);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AlertEventDescription> jobs = repository.findLockableJobs(now, PageRequest.of(0, batchSize));
        for (AlertEventDescription job : jobs) {
            if (dailySuccessCount.get() >= dailyJobLimit) {
                break;
            }
            job.markProcessing(now.plusSeconds(timeoutSeconds + 30));
            process(job);
        }
    }

    private void resetDailyCounterIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(dailyCountDay)) {
            dailyCountDay = today;
            dailySuccessCount.set(0);
        }
    }

    /**
     * Package-visible for pure unit tests. Mutates VLM job only — never AlertEvent status.
     * Uses job.maxRetries (set from {@code vlm.max-retry} at enqueue).
     */
    void process(AlertEventDescription job) {
        try {
            if (!VlmProcessSupport.hasRetryBudget(job.getRetryCount(), effectiveMaxRetry(job))) {
                job.markFailed("VLM max-retry budget exhausted (" + effectiveMaxRetry(job) + ")");
                return;
            }
            if (!embeddingService.canEmbed() && !mockMode) {
                job.markSkipped("VLM/Gemini credentials are missing and mock mode is disabled");
                return;
            }
            if (mockMode) {
                markMockSuccess(job);
                dailySuccessCount.incrementAndGet();
                return;
            }
            markProcessSuccess(job);
            if (job.getStatus() == com.strange.safety.vlm.entity.VlmJobStatus.SUCCESS) {
                dailySuccessCount.incrementAndGet();
            }
        } catch (Exception ex) {
            // Isolate failure to VLM job only — never mutate AlertEvent here.
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String msg = sanitizeLog(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            log.warn("[VLM] job failed id={} msg={}", job.getId(), msg);
            job.markFailed(msg);
        }
    }

    /** Prefer job.maxRetries (from enqueue / vlm.max-retry); fall back to scheduler config. */
    int effectiveMaxRetry(AlertEventDescription job) {
        int fromJob = job.getMaxRetries();
        if (fromJob > 0) {
            return fromJob;
        }
        return Math.max(1, maxRetry);
    }

    void markMockSuccess(AlertEventDescription job) {
        AlertEvent event = job.getAlertEvent();
        String scenarioType = event.getScenario() != null && event.getScenario().getScenarioType() != null
                ? event.getScenario().getScenarioType().name()
                : "UNKNOWN";
        String description = "Mock VLM description: safety event " + scenarioType
                + " shows a person or worker near the floor in a corridor or monitored area. "
                + "Korean keywords: 바닥, 쓰러짐, 안전모, 조끼, 복도.";
        String vlmJson = """
                {"visual_event_type":"person_lying_on_floor","people_count":1,
                "korean_search_keywords":["바닥","쓰러짐","안전모","조끼","복도"],
                "detailed_description_ko":"작업자 또는 사람이 감시 구역 바닥 근처에 있는 안전 이벤트로 보입니다.",
                "deidentificationMode":"PASSTHROUGH","deidentified":false,"safeForExternalProvider":false}
                """;
        double[] embedding = embeddingService.embed(description);
        job.markSuccess(
                vlmJson,
                description,
                embeddingService.encode(embedding),
                "",
                embeddingService.embeddingModelName(),
                true
        );
        pgVectorProjectionWriter.projectIfEnabled(job.getId(), embedding);
    }

    void markProcessSuccess(AlertEventDescription job) throws IOException, InterruptedException {
        Path scriptPath = Path.of(processScript).toAbsolutePath().normalize();
        Optional<String> missing = VlmProcessSupport.scriptMissingMessage(scriptPath);
        if (missing.isPresent()) {
            job.markFailed(missing.get());
            return;
        }
        String inputUrl = s3Service.generatePresignedUrl(job.getSourceAssetKey());
        List<String> keyframeKeys = buildKeyframeKeys(job);
        List<String> outputUrls = keyframeKeys.stream()
                .map(key -> s3Service.generatePresignedPutUrl(key, "image/jpeg", Duration.ofMinutes(15)))
                .toList();
        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                scriptPath.toString(),
                "--input-url", inputUrl,
                "--output-urls", String.join(",", outputUrls),
                "--metadata", metadataJson(job)
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        Optional<String> timedOut = VlmProcessSupport.timeoutMessage(finished, timeoutSeconds);
        if (timedOut.isPresent()) {
            process.destroyForcibly();
            job.markFailed(timedOut.get());
            return;
        }
        String stderr = readLimited(process.getErrorStream());
        String stdout = readLimited(process.getInputStream());
        if (process.exitValue() != 0) {
            job.markFailed(stderr.isBlank()
                    ? "VLM process exited with code " + process.exitValue()
                    : sanitizeLog(stderr));
            return;
        }
        JsonNode json = objectMapper.readTree(stdout);
        String description = json.path("detailed_description_ko").asText(json.toString());
        double[] embedding = embeddingService.embed(description);
        job.markSuccess(
                json.toString(),
                description,
                embeddingService.encode(embedding),
                String.join(",", keyframeKeys),
                embeddingService.embeddingModelName(),
                false
        );
        pgVectorProjectionWriter.projectIfEnabled(job.getId(), embedding);
    }

    private static String readLimited(InputStream stream) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while ((n = stream.read(chunk)) >= 0) {
            int allow = Math.min(n, MAX_STREAM_BYTES - total);
            if (allow > 0) {
                buf.write(chunk, 0, allow);
                total += allow;
            }
            if (total >= MAX_STREAM_BYTES) {
                break;
            }
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** Never log raw API keys or long secrets. */
    static String sanitizeLog(String message) {
        if (message == null) {
            return "";
        }
        String redacted = message.replaceAll("(?i)(api[_-]?key|token|secret|authorization)\\s*[=:]\\s*\\S+", "$1=***");
        if (redacted.length() > 2000) {
            return redacted.substring(0, 2000) + "...";
        }
        return redacted;
    }

    private List<String> buildKeyframeKeys(AlertEventDescription job) {
        List<String> keys = new ArrayList<>();
        int count = 6; // align with AI MAX_FRAMES=6
        for (int index = 0; index < count; index += 1) {
            keys.add("vlm/keyframes/" + job.getAlertEvent().getId() + "/" + job.getPromptVersion()
                    + "/" + index + ".jpg");
        }
        return keys;
    }

    private String metadataJson(AlertEventDescription job) throws IOException {
        AlertEvent event = job.getAlertEvent();
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("alert_event_id", event.getId());
        meta.put("source_type", job.getSourceAssetType().name());
        meta.put("source_key", job.getSourceAssetKey());
        meta.put("scenario_type", event.getScenario().getScenarioType().name());
        meta.put(
                "camera_login_id",
                event.getCamera() != null
                        ? event.getCamera().getCameraLoginId()
                        : event.getCorporateCamera().getCameraLoginId()
        );
        meta.put("bounding_box_data", event.getBoundingBoxData() == null ? "" : event.getBoundingBoxData());
        meta.put("keypoint_data", event.getKeypointData() == null ? "" : event.getKeypointData());
        meta.put("prompt_version", job.getPromptVersion());
        meta.put("deidentificationMode", "PASSTHROUGH");
        meta.put("deidentified", "false");
        meta.put("safeForExternalProvider", "false");
        return objectMapper.writeValueAsString(meta);
    }
}
