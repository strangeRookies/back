package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.service.S3Service;
import com.strange.safety.vlm.embedding.PgVectorProjectionWriter;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class VlmProcessingScheduler {
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

    @Scheduled(fixedDelayString = "${vlm.scheduler-delay-ms:15000}")
    @Transactional
    public void processPendingJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<AlertEventDescription> jobs = repository.findLockableJobs(now, PageRequest.of(0, batchSize));
        for (AlertEventDescription job : jobs) {
            job.markProcessing(now.plusSeconds(timeoutSeconds + 30));
            process(job);
        }
    }

    private void process(AlertEventDescription job) {
        if (!embeddingService.canEmbed()) {
            job.markSkipped("VLM/Gemini credentials are missing and mock mode is disabled");
            return;
        }
        if (mockMode) {
            markMockSuccess(job);
            return;
        }
        try {
            markProcessSuccess(job);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            job.markFailed(ex.getMessage());
        }
    }

    private void markMockSuccess(AlertEventDescription job) {
        AlertEvent event = job.getAlertEvent();
        String scenarioType = event.getScenario().getScenarioType().name();
        String description = "Mock VLM description: safety event " + scenarioType
                + " shows a person or worker near the floor in a corridor or monitored area. "
                + "Korean keywords: 바닥, 쓰러짐, 안전모, 조끼, 복도.";
        String vlmJson = """
                {"visual_event_type":"person_lying_on_floor","people_count":1,
                "korean_search_keywords":["바닥","쓰러짐","안전모","조끼","복도"],
                "detailed_description_ko":"작업자 또는 사람이 감시 구역 바닥 근처에 있는 안전 이벤트로 보입니다."}
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

    private void markProcessSuccess(AlertEventDescription job) throws IOException, InterruptedException {
        String inputUrl = s3Service.generatePresignedUrl(job.getSourceAssetKey());
        List<String> keyframeKeys = buildKeyframeKeys(job);
        List<String> outputUrls = keyframeKeys.stream()
                .map(key -> s3Service.generatePresignedPutUrl(key, "image/jpeg", Duration.ofMinutes(15)))
                .toList();
        Process process = new ProcessBuilder(
                pythonExecutable,
                processScript,
                "--input-url", inputUrl,
                "--output-urls", String.join(",", outputUrls),
                "--metadata", metadataJson(job)
        ).start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            job.markFailed("VLM process timed out after " + timeoutSeconds + " seconds");
            return;
        }
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            job.markFailed(stderr.isBlank() ? "VLM process exited with code " + process.exitValue() : stderr);
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

    private List<String> buildKeyframeKeys(AlertEventDescription job) {
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < 8; index += 1) {
            keys.add("vlm/keyframes/" + job.getAlertEvent().getId() + "/" + job.getPromptVersion()
                    + "/" + index + ".jpg");
        }
        return keys;
    }

    private String metadataJson(AlertEventDescription job) throws IOException {
        AlertEvent event = job.getAlertEvent();
        return objectMapper.writeValueAsString(java.util.Map.of(
                "alert_event_id", event.getId(),
                "source_type", job.getSourceAssetType().name(),
                "source_key", job.getSourceAssetKey(),
                "scenario_type", event.getScenario().getScenarioType().name(),
                "camera_login_id", event.getCamera() != null
                        ? event.getCamera().getCameraLoginId()
                        : event.getCorporateCamera().getCameraLoginId(),
                "bounding_box_data", event.getBoundingBoxData() == null ? "" : event.getBoundingBoxData(),
                "keypoint_data", event.getKeypointData() == null ? "" : event.getKeypointData(),
                "prompt_version", job.getPromptVersion()
        ));
    }
}
