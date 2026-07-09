package com.strange.safety.vlm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {
    private static final int DIMENSION = 32;
    private static final List<String> KEYWORDS = List.of(
            "yellow", "blue", "floor", "helmet", "vest", "lying", "fall", "collapse",
            "hallway", "corridor", "worker", "person", "door", "wall", "safety", "위험",
            "노란", "파란", "바닥", "안전모", "조끼", "누움", "쓰러", "복도",
            "작업자", "사람", "출입문", "벽", "낙상", "실신", "이탈", "폭행"
    );

    @Value("${vlm.mock-mode:${VLM_MOCK_MODE:true}}")
    private boolean mockMode;

    @Value("${vlm.gemini-api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    public boolean canEmbed() {
        return mockMode || (geminiApiKey != null && !geminiApiKey.isBlank());
    }

    public String embeddingModelName() {
        return mockMode ? "mock" : "gemini-text-embedding-004";
    }

    public double[] embed(String text) {
        if (!mockMode) {
            throw new IllegalStateException("Production embedding client is not configured in this MVP");
        }
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
