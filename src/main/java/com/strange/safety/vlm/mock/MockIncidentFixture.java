package com.strange.safety.vlm.mock;

import java.time.LocalDateTime;
import java.util.List;

public final class MockIncidentFixture {
    private MockIncidentFixture() {
    }

    public static List<Incident> incidents() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 10, 12, 30);
        return List.of(
                incident(
                        "incident-demo-001",
                        "orig-fall-recovered",
                        "cam_02",
                        "출입문 D",
                        IncidentStatus.RECOVERED,
                        base,
                        List.of(IncidentEventType.NEW_FALL, IncidentEventType.RECOVERED)
                ),
                incident(
                        "incident-demo-002",
                        "orig-faint-suspected",
                        "cam_02",
                        "복도 B",
                        IncidentStatus.OPEN,
                        base.plusMinutes(12),
                        List.of(IncidentEventType.NEW_FALL, IncidentEventType.FAINT_SUSPECTED)
                ),
                incident(
                        "incident-demo-003",
                        "orig-unrecovered",
                        "cam_03",
                        "작업구역 A",
                        IncidentStatus.UNRECOVERED,
                        base.plusMinutes(24),
                        List.of(IncidentEventType.NEW_FALL, IncidentEventType.FALL_UNRECOVERED)
                ),
                incident(
                        "incident-demo-004",
                        "orig-normal",
                        "cam_04",
                        "휴게 공간",
                        IncidentStatus.EXCLUDED,
                        base.plusMinutes(36),
                        List.of(IncidentEventType.NORMAL)
                )
        );
    }

    public static MockRuntime load() {
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        InMemoryVlmJobRepository jobs = new InMemoryVlmJobRepository();
        InMemoryVlmDescriptionRepository descriptions = new InMemoryVlmDescriptionRepository();
        MockEmbeddingRepository embeddings = new MockEmbeddingRepository();
        MockVlmClient vlmClient = new MockVlmClient();
        IncidentSearchDocumentBuilder documentBuilder = new IncidentSearchDocumentBuilder();
        for (Incident incident : incidents()) {
            Incident saved = incidents.save(incident);
            VlmAnalysisResult result = vlmClient.analyze(saved);
            MediaAsset mediaAsset = saved.mediaAssets().getFirst();
            jobs.save(new VlmJob("job-" + saved.incidentId(), saved, mediaAsset, result));
            descriptions.save(documentBuilder.build(saved, result, embeddings));
        }
        MockSemanticSearchService search = new MockSemanticSearchService(incidents, descriptions, embeddings);
        return new MockRuntime(incidents, jobs, descriptions, embeddings, search);
    }

    private static Incident incident(
            String incidentId,
            String originalEventId,
            String cameraLoginId,
            String locationName,
            IncidentStatus status,
            LocalDateTime detectedAt,
            List<IncidentEventType> eventTypes
    ) {
        List<IncidentEvent> events = eventTypes.stream()
                .map(type -> event(incidentId, originalEventId, cameraLoginId, detectedAt, type))
                .toList();
        MediaAsset asset = new MediaAsset(
                "asset-" + incidentId,
                incidentId,
                "mock-media/" + incidentId + ".mp4",
                "MOCK_CLIP",
                List.of(0, 2, 3, 4, 6, 7, 8, 9)
        );
        return new Incident(
                incidentId,
                originalEventId,
                cameraLoginId,
                locationName,
                status,
                detectedAt,
                events,
                List.of(asset)
        );
    }

    private static IncidentEvent event(
            String incidentId,
            String originalEventId,
            String cameraLoginId,
            LocalDateTime detectedAt,
            IncidentEventType type
    ) {
        return new IncidentEvent(
                incidentId + "-" + type.name().toLowerCase(),
                originalEventId,
                cameraLoginId,
                "track-7",
                type,
                detectedAt
        );
    }

    public record MockRuntime(
            IncidentRepository incidentRepository,
            VlmJobRepository vlmJobRepository,
            VlmDescriptionRepository vlmDescriptionRepository,
            EmbeddingRepository embeddingRepository,
            MockSemanticSearchService searchService
    ) {
    }
}
