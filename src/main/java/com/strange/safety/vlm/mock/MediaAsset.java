package com.strange.safety.vlm.mock;

import java.util.List;
import java.util.Objects;

public record MediaAsset(
        String assetId,
        String incidentId,
        String sourceKey,
        String mediaType,
        List<Integer> keyframeIndexes
) {
    public MediaAsset {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(mediaType, "mediaType");
        keyframeIndexes = List.copyOf(keyframeIndexes);
    }
}
