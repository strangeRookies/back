package com.strange.safety.vlm.mock;

import com.strange.safety.common.response.ApiResponse;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockSemanticSearchController {
    private final MockIncidentFixture.MockRuntime runtime;

    public MockSemanticSearchController() {
        this(MockIncidentFixture.load());
    }

    MockSemanticSearchController(MockIncidentFixture.MockRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/api/mock/vlm/search")
    public ResponseEntity<ApiResponse<List<MockSemanticSearchResultResponse>>> search(
            @RequestParam String query,
            @RequestParam(required = false) String cameraLoginIds,
            @RequestParam(required = false) String eventTypes,
            @RequestParam(required = false) String incidentStatuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "0.01") double minSimilarity
    ) {
        SearchRequest request = new SearchRequest(
                query,
                split(cameraLoginIds),
                parseEnums(eventTypes, IncidentEventType::valueOf),
                parseEnums(incidentStatuses, IncidentStatus::valueOf),
                dateFrom,
                dateTo,
                topK,
                minSimilarity
        );
        List<MockSemanticSearchResultResponse> results = runtime.searchService().search(request).stream()
                .map(MockSemanticSearchResultResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    private static Set<String> split(String values) {
        if (values == null || values.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <T extends Enum<T>> Set<T> parseEnums(String values, Function<String, T> parser) {
        return split(values).stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .map(parser)
                .collect(Collectors.toUnmodifiableSet());
    }
}
