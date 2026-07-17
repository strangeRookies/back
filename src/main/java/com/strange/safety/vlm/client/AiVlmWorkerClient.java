package com.strange.safety.vlm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.vlm.dto.VlmIndexPayload;
import com.strange.safety.vlm.service.VlmIndexPayloadParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class AiVlmWorkerClient {

    private final ObjectMapper objectMapper;
    private final VlmIndexPayloadParser payloadParser;
    private final HttpClient httpClient;

    @Value("${vlm.ai-worker.base-url:}")
    private String baseUrl;

    @Value("${vlm.ai-worker.service-token:}")
    private String serviceToken;

    @Value("${vlm.ai-worker.timeout-seconds:180}")
    private long timeoutSeconds;

    public AiVlmWorkerClient(ObjectMapper objectMapper, VlmIndexPayloadParser payloadParser) {
        this.objectMapper = objectMapper;
        this.payloadParser = payloadParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public VlmIndexPayload processClipJob(
            long jobId,
            String incidentId,
            String cameraLoginId,
            String clipPresignedUrl,
            String scenarioType,
            String severity,
            String capturedAtIso,
            Double clipStartSec,
            Double clipEndSec
    ) throws AiVlmWorkerException {
        if (!isConfigured()) {
            throw new AiVlmWorkerException("ai_worker_base_url_missing", false, null);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jobId", jobId);
            body.put("incidentId", incidentId);
            body.put("cameraLoginId", cameraLoginId);
            body.put("clipUrl", clipPresignedUrl);
            body.put("scenarioType", scenarioType);
            body.put("severity", severity);
            body.put("capturedAt", capturedAtIso);
            if (clipStartSec != null) {
                body.put("clipStartSec", clipStartSec);
            }
            if (clipEndSec != null) {
                body.put("clipEndSec", clipEndSec);
            }

            String url = baseUrl.replaceAll("/+$", "") + "/internal/vlm/jobs";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            if (serviceToken != null && !serviceToken.isBlank()) {
                builder.header("X-Service-Token", serviceToken.trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 408 || status == 429 || status >= 500) {
                throw new AiVlmWorkerException(workerFailureCode(status, response.body(), true), true, status);
            }
            if (status < 200 || status >= 300) {
                throw new AiVlmWorkerException(workerFailureCode(status, response.body(), false), false, status);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                throw new AiVlmWorkerException("ai_worker_job_error", false, null);
            }
            String json = objectMapper.writeValueAsString(root);
            boolean mock = root.path("vlm_result").path("is_mock").asBoolean(false);
            return payloadParser.parseAndValidate(
                    json,
                    new VlmIndexPayloadParser.Expected(
                            incidentId,
                            cameraLoginId,
                            OffsetDateTime.parse(capturedAtIso),
                            mock)
            );
        } catch (AiVlmWorkerException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiVlmWorkerException("interrupted", false, null);
        } catch (Exception ex) {
            log.warn("[AiVlmWorker] request failed jobId={}: {}", jobId, ex.getMessage());
            throw new AiVlmWorkerException("ai_worker_request_failed", false, null);
        }
    }

    private String workerFailureCode(int status, String responseBody, boolean transientFailure) {
        String prefix = transientFailure ? "ai_worker_transient_http_" : "ai_worker_http_";
        String message = "";
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            message = root.path("message").asText("");
        } catch (Exception ignored) {
            // Keep the status-only failure code when the worker did not return JSON.
        }
        message = message
                .replaceAll("https?://\\S+", "[url]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (message.length() > 160) {
            message = message.substring(0, 160);
        }
        return prefix + status + (message.isBlank() ? "" : ":" + message);
    }

    public static final class AiVlmWorkerException extends Exception {
        private final boolean transientFailure;
        private final Integer httpStatus;

        public AiVlmWorkerException(String code, boolean transientFailure) {
            this(code, transientFailure, null);
        }

        public AiVlmWorkerException(String code, boolean transientFailure, Integer httpStatus) {
            super(code);
            this.transientFailure = transientFailure;
            this.httpStatus = httpStatus;
        }

        public boolean isTransient() {
            return transientFailure;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        public boolean isRateLimited() {
            return httpStatus != null && httpStatus == 429;
        }
    }
}