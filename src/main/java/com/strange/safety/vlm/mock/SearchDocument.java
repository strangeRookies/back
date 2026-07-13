package com.strange.safety.vlm.mock;

import java.util.Objects;

public record SearchDocument(String incidentId, String documentText, double[] embedding) {
    public SearchDocument {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(documentText, "documentText");
        embedding = embedding.clone();
    }
}
