package com.strange.safety.vlm.embedding;

/**
 * Direct embedding provider (no LangChain).
 * Implementations call vendor SDKs/HTTP APIs or a deterministic mock.
 */
public interface EmbeddingClient {

    boolean available();

    String modelName();

    int dimension();

    double[] embed(String text);
}
