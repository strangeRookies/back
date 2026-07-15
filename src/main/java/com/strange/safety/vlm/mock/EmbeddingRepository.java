package com.strange.safety.vlm.mock;

public interface EmbeddingRepository {
    double[] embed(String text);

    int dimension();
}
