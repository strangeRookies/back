package com.strange.safety.vlm.mock;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MockSemanticSearchService {
    private final IncidentRepository incidentRepository;
    private final VlmDescriptionRepository descriptionRepository;
    private final EmbeddingRepository embeddingRepository;

    public MockSemanticSearchService(
            IncidentRepository incidentRepository,
            VlmDescriptionRepository descriptionRepository,
            EmbeddingRepository embeddingRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.descriptionRepository = descriptionRepository;
        this.embeddingRepository = embeddingRepository;
    }

    public List<SearchResult> search(SearchRequest request) {
        double[] queryEmbedding = embeddingRepository.embed(request.query());
        Map<String, Incident> incidents = incidentRepository.findAll().stream()
                .collect(Collectors.toMap(Incident::incidentId, Function.identity()));
        return descriptionRepository.findAll().stream()
                .map(document -> toResult(document, incidents.get(document.incidentId()), queryEmbedding))
                .filter(result -> result.incident() != null)
                .filter(result -> result.similarityScore() >= request.minSimilarity())
                .filter(result -> matchesMetadata(result.incident(), request))
                .collect(Collectors.toMap(result -> result.incident().incidentId(), Function.identity(),
                        (left, right) -> left.similarityScore() >= right.similarityScore() ? left : right))
                .values().stream()
                .sorted(Comparator.comparingDouble(SearchResult::similarityScore).reversed())
                .limit(Math.max(1, request.topK()))
                .toList();
    }

    private SearchResult toResult(SearchDocument document, Incident incident, double[] queryEmbedding) {
        return new SearchResult(incident, document, cosine(queryEmbedding, document.embedding()));
    }

    private boolean matchesMetadata(Incident incident, SearchRequest request) {
        boolean cameraMatches = request.cameraLoginIds().isEmpty()
                || request.cameraLoginIds().contains(incident.cameraLoginId());
        boolean statusMatches = request.incidentStatuses().isEmpty()
                || request.incidentStatuses().contains(incident.status());
        boolean eventMatches = request.eventTypes().isEmpty()
                || incident.events().stream().anyMatch(event -> request.eventTypes().contains(event.eventType()));
        boolean fromMatches = request.from() == null || !incident.detectedAt().isBefore(request.from());
        boolean toMatches = request.to() == null || !incident.detectedAt().isAfter(request.to());
        return cameraMatches && statusMatches && eventMatches && fromMatches && toMatches;
    }

    private double cosine(double[] left, double[] right) {
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < Math.min(left.length, right.length); index += 1) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return leftNorm == 0.0d || rightNorm == 0.0d
                ? 0.0d
                : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
