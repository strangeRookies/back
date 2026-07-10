package com.strange.safety.vlm.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini text embedding via REST (direct HTTP — no LangChain).
 * Enabled when {@code vlm.embedding-provider=gemini}.
 */
@Component
@ConditionalOnProperty(name = "vlm.embedding-provider", havingValue = "gemini")
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final String MODEL = "text-embedding-004";
    private static final int DIMENSION = 768;

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiEmbeddingClient(
            @Value("${vlm.gemini-api-key:${GEMINI_API_KEY:}}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public boolean available() {
        return !apiKey.isBlank();
    }

    @Override
    public String modelName() {
        return "gemini-" + MODEL;
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public double[] embed(String text) {
        if (!available()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing for embedding-provider=gemini");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", "models/" + MODEL,
                    "content", Map.of("parts", List.of(Map.of("text", text == null ? "" : text)))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/"
                                    + MODEL + ":embedContent?key=" + apiKey))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini embed HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode values = objectMapper.readTree(response.body())
                    .path("embedding")
                    .path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Gemini embed response missing embedding.values");
            }
            List<Double> list = new ArrayList<>(values.size());
            values.forEach(node -> list.add(node.asDouble()));
            double[] vector = new double[list.size()];
            for (int i = 0; i < list.size(); i += 1) {
                vector[i] = list.get(i);
            }
            return vector;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Gemini embed failed: " + ex.getMessage(), ex);
        }
    }
}
