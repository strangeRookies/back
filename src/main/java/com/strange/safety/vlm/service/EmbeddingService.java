package com.strange.safety.vlm.service;

import com.strange.safety.vlm.embedding.EmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Facade over {@link EmbeddingClient}. Direct SDK clients only — no LangChain.
 */
@Service
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;

    public EmbeddingService(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public boolean canEmbed() {
        return embeddingClient.available();
    }

    public String embeddingModelName() {
        return embeddingClient.modelName();
    }

    public int dimension() {
        return embeddingClient.dimension();
    }

    public double[] embed(String text) {
        return embeddingClient.embed(text);
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
