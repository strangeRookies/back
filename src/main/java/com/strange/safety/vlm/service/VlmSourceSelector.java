package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.VlmSourceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class VlmSourceSelector {

    public Optional<VlmSource> select(AlertEvent event) {
        Optional<String> clipPathKey = normalizeS3Key(event.getClipPath());
        if (clipPathKey.isPresent()) {
            return Optional.of(new VlmSource(VlmSourceType.CLIP, clipPathKey.get()));
        }

        Optional<String> clipUrlKey = normalizeS3Key(event.getClipUrl());
        return clipUrlKey.map(key -> new VlmSource(VlmSourceType.CLIP, key));
    }

    public Optional<String> normalizeS3Key(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("s3://")) {
            return Optional.of(trimmed.startsWith("/") ? trimmed.substring(1) : trimmed);
        }
        if (trimmed.startsWith("s3://")) {
            int slashIndex = trimmed.indexOf('/', "s3://".length());
            return slashIndex > 0 && slashIndex + 1 < trimmed.length()
                    ? Optional.of(trimmed.substring(slashIndex + 1))
                    : Optional.empty();
        }
        try {
            URI uri = new URI(trimmed);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return Optional.empty();
            }
            return Optional.of(path.startsWith("/") ? path.substring(1) : path);
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    public record VlmSource(VlmSourceType sourceType, String sourceKey) {
    }
}
