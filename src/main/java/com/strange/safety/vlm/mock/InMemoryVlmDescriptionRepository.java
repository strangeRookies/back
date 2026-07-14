package com.strange.safety.vlm.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryVlmDescriptionRepository implements VlmDescriptionRepository {
    private final Map<String, SearchDocument> documentsByIncidentId = new LinkedHashMap<>();

    @Override
    public SearchDocument save(SearchDocument document) {
        documentsByIncidentId.put(document.incidentId(), document);
        return document;
    }

    @Override
    public List<SearchDocument> findAll() {
        return new ArrayList<>(documentsByIncidentId.values());
    }
}
