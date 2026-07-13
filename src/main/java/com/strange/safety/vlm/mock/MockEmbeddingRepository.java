package com.strange.safety.vlm.mock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

public class MockEmbeddingRepository implements EmbeddingRepository {
    public static final int DIMENSION = 768;

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        for (String token : normalizedTokens(text)) {
            byte[] digest = sha256(token);
            int index = Math.floorMod(ByteBuffer.wrap(digest, 0, Integer.BYTES).getInt(), DIMENSION);
            int sign = (digest[4] & 1) == 0 ? 1 : -1;
            vector[index] += sign;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    private String[] normalizedTokens(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_]+", " ")
                .trim();
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private void normalize(double[] vector) {
        double sumSquares = 0.0d;
        for (double value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0.0d) {
            return;
        }
        double norm = Math.sqrt(sumSquares);
        for (int index = 0; index < vector.length; index += 1) {
            vector[index] = vector[index] / norm;
        }
    }
}
