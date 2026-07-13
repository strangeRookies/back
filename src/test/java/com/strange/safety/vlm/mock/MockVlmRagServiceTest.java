package com.strange.safety.vlm.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MockVlmRagServiceTest {
    @Test
    void groupsEventsIntoOneIncidentWhenOriginalEventIdMatches() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident first = MockIncidentFixture.incidents().get(0);
        Incident duplicate = new Incident(
                "incident-duplicate",
                first.originalEventId(),
                first.cameraLoginId(),
                first.locationName(),
                first.status(),
                first.detectedAt(),
                first.events(),
                first.mediaAssets()
        );

        repository.save(first);
        Incident saved = repository.save(duplicate);

        assertThat(saved.incidentId()).isEqualTo(first.incidentId());
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void recoveredTimelineChangesMockVlmResult() {
        MockVlmClient client = new MockVlmClient();
        Incident recovered = MockIncidentFixture.incidents().get(0);
        Incident unrecovered = MockIncidentFixture.incidents().get(2);

        VlmAnalysisResult recoveredResult = client.analyze(recovered);
        VlmAnalysisResult unrecoveredResult = client.analyze(unrecovered);

        assertThat(recoveredResult.schemaVersion()).isEqualTo("incident-v1");
        assertThat(recoveredResult.recoveryObserved()).isTrue();
        assertThat(recoveredResult.movementAfterEvent()).isEqualTo("medium");
        assertThat(unrecoveredResult.recoveryObserved()).isFalse();
        assertThat(unrecoveredResult.movementAfterEvent()).isEqualTo("low");
    }

    @Test
    void sameSearchDocumentProducesSame768DimensionVector() {
        MockEmbeddingRepository repository = new MockEmbeddingRepository();

        double[] first = repository.embed("쓰러진 뒤 움직임 low recovered");
        double[] second = repository.embed("쓰러진 뒤 움직임 low recovered");
        double[] different = repository.embed("정상 보행 제외");

        assertThat(first).hasSize(768);
        assertThat(first).containsExactly(second);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void searchReturnsIncidentLevelResultsWithoutDuplicatesAndHonorsFilters() {
        MockIncidentFixture.MockRuntime runtime = MockIncidentFixture.load();
        SearchRequest request = new SearchRequest(
                "쓰러진 뒤 움직임 낮음 출입문 회복",
                Set.of("cam_02"),
                Set.of(IncidentEventType.NEW_FALL),
                Set.of(IncidentStatus.RECOVERED, IncidentStatus.OPEN),
                null,
                null,
                10,
                0.01d
        );

        var results = runtime.searchService().search(request);

        assertThat(results)
                .extracting(result -> result.incident().incidentId())
                .doesNotHaveDuplicates()
                .contains("incident-demo-001", "incident-demo-002")
                .doesNotContain("incident-demo-003", "incident-demo-004");
    }

    @Test
    void noResultQueryReturnsEmptyList() {
        MockIncidentFixture.MockRuntime runtime = MockIncidentFixture.load();
        SearchRequest request = new SearchRequest(
                "helmet forklift ocean unrelated",
                Set.of("cam_02"),
                Set.of(IncidentEventType.NORMAL),
                Set.of(IncidentStatus.RECOVERED),
                null,
                null,
                10,
                0.01d
        );

        assertThat(runtime.searchService().search(request)).isEmpty();
    }

    @Test
    void mockSearchControllerExposesFrontendCompatibleResultShape() {
        MockSemanticSearchController controller = new MockSemanticSearchController(MockIncidentFixture.load());

        ResponseEntity<?> response = controller.search(
                "fall low movement",
                "cam_02",
                "NEW_FALL",
                "RECOVERED,OPEN",
                null,
                null,
                10,
                0.01d
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Object body = response.getBody();
        assertThat(body).hasFieldOrProperty("data");
    }
}
