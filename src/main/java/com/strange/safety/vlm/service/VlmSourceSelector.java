package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.vlm.entity.VlmSourceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Selects media source for VLM: clip first, then valid <em>image</em> snapshot only.
 * Rejects legacy rows where snapshotUrl holds a clip key/URL.
 */
public class VlmSourceSelector {

    public Optional<VlmSource> select(AlertEvent event) {
        Optional<String> clipPathKey = normalizeS3Key(event.getClipPath());
        if (clipPathKey.isPresent() && !looksLikeVideo(clipPathKey.get(), null)) {
            // clip path can be local; still prefer as CLIP type if non-empty
            return Optional.of(new VlmSource(VlmSourceType.CLIP, clipPathKey.get()));
        }
        if (clipPathKey.isPresent()) {
            return Optional.of(new VlmSource(VlmSourceType.CLIP, clipPathKey.get()));
        }

        Optional<String> clipUrlKey = normalizeS3Key(event.getClipUrl());
        if (clipUrlKey.isPresent()) {
            return Optional.of(new VlmSource(VlmSourceType.CLIP, clipUrlKey.get()));
        }

        if (event.getSnapshots() != null) {
            for (Snapshot snapshot : event.getSnapshots()) {
                Optional<String> key = normalizeS3Key(snapshot.getSnapshotUrl());
                if (key.isEmpty()) {
                    continue;
                }
                if (looksLikeVideo(key.get(), snapshot.getSnapshotUrl())) {
                    continue; // legacy: clip key stored in snapshotUrl
                }
                return Optional.of(new VlmSource(VlmSourceType.SNAPSHOT, key.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * True when the candidate is a video asset (must not be treated as image snapshot).
     */
    public boolean looksLikeVideo(String objectKeyOrPath, String rawValue) {
        String probe = ((objectKeyOrPath == null ? "" : objectKeyOrPath) + " "
                + (rawValue == null ? "" : rawValue)).toLowerCase(Locale.ROOT);
        if (probe.contains("/clips/") || probe.contains("\\clips\\")) {
            return true;
        }
        if (probe.contains("content-type=video") || probe.contains("contenttype=video")
                || probe.contains("video/mp4") || probe.contains("video/*")) {
            return true;
        }
        return probe.contains(".mp4")
                || probe.contains(".mov")
                || probe.contains(".avi")
                || probe.contains(".mkv")
                || probe.contains(".webm");
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
