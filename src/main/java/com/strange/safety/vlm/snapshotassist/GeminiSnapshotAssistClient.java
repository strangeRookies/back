package com.strange.safety.vlm.snapshotassist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini Vision REST for snapshot-assist-v1 (no LangChain).
 */
@Component
public class GeminiSnapshotAssistClient {

    private static final String SCHEMA_VERSION = "snapshot-assist-v1";
    private static final int MAX_SUMMARY_LEN = 500;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiSnapshotAssistClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SnapshotAssistAnalysisResult analyze(
            byte[] jpegBytes,
            SnapshotAssistContext context,
            String apiKey,
            String model
    ) throws GeminiSnapshotAssistException {
        if (jpegBytes == null || jpegBytes.length < 100) {
            throw new GeminiSnapshotAssistException("invalid_jpeg", false);
        }
        String effectiveModel = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model.trim();
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(jpegBytes);
            Map<String, Object> payload = buildRequestPayload(b64, context);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + effectiveModel + ":generateContent?key=" + apiKey.trim();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 408 || status == 429 || status >= 500) {
                throw new GeminiSnapshotAssistException("gemini_transient_http_" + status, true);
            }
            if (status < 200 || status >= 300) {
                throw new GeminiSnapshotAssistException("gemini_http_" + status, false);
            }
            return parseModelJson(extractText(response.body()));
        } catch (GeminiSnapshotAssistException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiSnapshotAssistException("interrupted", false);
        } catch (Exception ex) {
            throw new GeminiSnapshotAssistException("gemini_request_failed", false);
        }
    }

    private Map<String, Object> buildRequestPayload(String b64, SnapshotAssistContext ctx) {
        String prompt = """
                You are a safety CCTV assistant. You do NOT cancel or reclassify the primary AI alert.
                Describe only what is visible in this single de-identified snapshot.
                Do not infer identity, age, gender, disease, or medical diagnosis.
                Use phrases like "한 사람", "작업자로 보이는 사람" — never personal names.
                Use the provided AI detector fields as given facts; do not invent probabilities.
                Respond with JSON only matching snapshot-assist-v1.
                Fields: schemaVersion, visualCategory, personCount (int), observations (string array),
                summaryKo (1-2 Korean sentences), limitationsKo (one Korean sentence).
                """
                + "\nAI context: " + ctx.toPromptLine();

        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", b64)
        );
        Map<String, Object> textPart = Map.of("text", prompt);
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(textPart, imagePart))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2
                )
        );
    }

    private String extractText(String body) throws GeminiSnapshotAssistException {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new GeminiSnapshotAssistException("empty_gemini_response", false);
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) {
                throw new GeminiSnapshotAssistException("empty_gemini_text", false);
            }
            return text;
        } catch (GeminiSnapshotAssistException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeminiSnapshotAssistException("gemini_response_parse_failed", false);
        }
    }

    private SnapshotAssistAnalysisResult parseModelJson(String jsonText) throws GeminiSnapshotAssistException {
        try {
            String trimmed = jsonText.trim();
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            JsonNode node = objectMapper.readTree(trimmed);
            String summary = node.path("summaryKo").asText("").trim();
            if (summary.isEmpty()) {
                throw new GeminiSnapshotAssistException("empty_summary_ko", false);
            }
            if (summary.length() > MAX_SUMMARY_LEN) {
                summary = summary.substring(0, MAX_SUMMARY_LEN);
            }
            String limitations = node.path("limitationsKo").asText("").trim();
            String category = node.path("visualCategory").asText("").trim();
            return new SnapshotAssistAnalysisResult(SCHEMA_VERSION, category, summary, limitations);
        } catch (GeminiSnapshotAssistException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeminiSnapshotAssistException("invalid_snapshot_assist_json", false);
        }
    }

    public record SnapshotAssistContext(
            String eventId,
            String cameraLoginId,
            String eventType,
            String trackId,
            Double confidence,
            Double faintProbability,
            String lifecycleState,
            Integer consecutiveCount,
            String detectorReason,
            String capturedAt
    ) {
        String toPromptLine() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("eventId", eventId);
            fields.put("cameraLoginId", cameraLoginId);
            fields.put("eventType", eventType);
            fields.put("trackId", trackId);
            if (confidence != null) {
                fields.put("confidence", String.valueOf(confidence));
            }
            if (faintProbability != null) {
                fields.put("faintProbability", String.valueOf(faintProbability));
            }
            fields.put("lifecycleState", lifecycleState);
            if (consecutiveCount != null) {
                fields.put("consecutiveCount", String.valueOf(consecutiveCount));
            }
            fields.put("detectorReason", detectorReason);
            fields.put("capturedAt", capturedAt);
            return fields.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
        }
    }

    public record SnapshotAssistAnalysisResult(
            String schemaVersion,
            String visualCategory,
            String summaryKo,
            String limitationsKo
    ) {
    }

    public static final class GeminiSnapshotAssistException extends Exception {
        private final boolean transientFailure;

        public GeminiSnapshotAssistException(String code, boolean transientFailure) {
            super(code);
            this.transientFailure = transientFailure;
        }

        public boolean isTransient() {
            return transientFailure;
        }
    }
}