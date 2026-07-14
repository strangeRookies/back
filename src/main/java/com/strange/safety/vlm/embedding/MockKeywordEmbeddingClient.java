package com.strange.safety.vlm.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic keyword bag-of-features for local/dev (not semantic quality).
 * Default when {@code vlm.embedding-provider=mock} (or unset).
 */
@Component
@ConditionalOnProperty(name = "vlm.embedding-provider", havingValue = "mock", matchIfMissing = true)
public class MockKeywordEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSION = 32;
    private static final List<String> KEYWORDS = List.of(
            "yellow", "blue", "floor", "helmet", "vest", "lying", "fall", "collapse",
            "hallway", "corridor", "worker", "person", "door", "wall", "safety", "위험",
            "노란", "파란", "바닥", "안전모", "조끼", "누움", "쓰러", "복도",
            "작업자", "사람", "출입문", "벽", "낙상", "실신", "이탈", "폭행"
    );

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String modelName() {
        return "mock-keyword-32";
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (int index = 0; index < KEYWORDS.size(); index += 1) {
            if (lower.contains(KEYWORDS.get(index))) {
                vector[index] = 1.0d;
            }
        }
        return vector;
    }
}
