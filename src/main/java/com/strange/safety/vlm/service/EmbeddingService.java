package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Facade for embedding operations. Direct SDK clients only — no LangChain.
 */
@Service
public class EmbeddingService {
    private static final int DIMENSION = VlmIndexPayloadParser.EMBEDDING_DIMENSION;
    private static final List<String> KEYWORDS = List.of(
            "yellow", "blue", "floor", "helmet", "vest", "lying", "fall", "collapse",
            "hallway", "corridor", "worker", "person", "door", "wall", "safety", "위험",
            "노란", "파란", "바닥", "안전모", "조끼", "누움", "쓰러", "복도",
            "작업자", "사람", "출입문", "벽", "낙상", "실신", "이탈", "폭행"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${vlm.mock-mode:${VLM_MOCK_MODE:true}}")
    private boolean mockMode;

    @Value("${vlm.query-embedding-model:${VLM_QUERY_EMBEDDING_MODEL:text-embedding-004}}")
    private String queryEmbeddingModel;

    @Value("${vlm.gemini-api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    public EmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean canEmbed() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public String embeddingModelName() {
        return mockMode ? "mock-vlm-index-768" : "gemini-" + queryEmbeddingModel;
    }

    public int dimension() {
        return DIMENSION;
    }

    public double[] embed(String text) {
        if (mockMode) {
            return mockEmbedding(text);
        }
        if (!canEmbed()) {
            throw new IllegalStateException("Query embedding credentials are not configured");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "models/" + queryEmbeddingModel);
            body.putObject("content").putArray("parts").addObject().put("text", text == null ? "" : text);
            body.put("outputDimensionality", DIMENSION);
            URI endpoint = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + URLEncoder.encode(queryEmbeddingModel, StandardCharsets.UTF_8) + ":embedContent");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", geminiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Query embedding provider returned HTTP " + response.statusCode());
            }
            JsonNode values = objectMapper.readTree(response.body()).path("embedding").path("values");
            if (!values.isArray() || values.size() != DIMENSION) {
                throw new IllegalStateException("Query embedding provider returned an invalid dimension");
            }
            double[] vector = new double[DIMENSION];
            for (int index = 0; index < DIMENSION; index += 1) {
                vector[index] = values.get(index).doubleValue();
                if (!Double.isFinite(vector[index])) {
                    throw new IllegalStateException("Query embedding provider returned a non-finite value");
                }
            }
            return vector;
        } catch (IOException ex) {
            throw new IllegalStateException("Query embedding provider response was invalid", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Query embedding request was interrupted", ex);
        }
    }

    private double[] mockEmbedding(String text) {
        double[] vector = new double[DIMENSION];
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (int index = 0; index < KEYWORDS.size(); index += 1) {
            if (lower.contains(KEYWORDS.get(index))) {
                vector[index] = 1.0d;
            }
        }
        return vector;
    }

    public String encode(double[] vector) {
        return Arrays.stream(vector)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(","));
    }

    public double[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new double[0];
        }
        String[] parts = encoded.split(",");
        double[] vector = new double[parts.length];
        for (int index = 0; index < parts.length; index += 1) {
            vector[index] = Double.parseDouble(parts[index]);
        }
        return vector;
    }

    public double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < length; index += 1) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
