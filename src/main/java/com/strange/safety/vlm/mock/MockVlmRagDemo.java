package com.strange.safety.vlm.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Set;

public final class MockVlmRagDemo {
    private MockVlmRagDemo() {
    }

    public static void main(String[] args) throws JsonProcessingException {
        MockIncidentFixture.MockRuntime runtime = MockIncidentFixture.load();
        SearchRequest request = new SearchRequest(
                "출입문에서 쓰러진 뒤 움직임이 낮고 회복된 사람",
                Set.of("cam_02"),
                Set.of(IncidentEventType.NEW_FALL, IncidentEventType.RECOVERED),
                Set.of(IncidentStatus.RECOVERED, IncidentStatus.OPEN),
                null,
                null,
                5,
                0.01d
        );
        List<SearchResult> results = runtime.searchService().search(request);
        DemoResponse response = new DemoResponse(
                runtime.incidentRepository().findAll(),
                runtime.vlmJobRepository().findAll(),
                runtime.vlmDescriptionRepository().findAll(),
                results
        );
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    private record DemoResponse(
            List<Incident> sampleIncidents,
            List<VlmJob> mockVlmJobs,
            List<SearchDocument> searchDocuments,
            List<SearchResult> searchResults
    ) {
    }
}
