package com.strange.safety.vlm.mock;

import java.util.List;

public interface VlmDescriptionRepository {
    SearchDocument save(SearchDocument document);

    List<SearchDocument> findAll();
}
