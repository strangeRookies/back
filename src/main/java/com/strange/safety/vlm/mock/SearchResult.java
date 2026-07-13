package com.strange.safety.vlm.mock;

public record SearchResult(Incident incident, SearchDocument document, double similarityScore) {
}
