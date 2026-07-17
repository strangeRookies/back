package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.VlmSourceType;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class VlmSourceSelector {

    public Optional<VlmSource> select(AlertEvent event) {
        Optional<String> clipObjectKey = normalizeObjectKey(event.getClipObjectKey());
        if (clipObjectKey.isPresent()) {
            return Optional.of(new VlmSource(VlmSourceType.CLIP, clipObjectKey.get()));
        }
        return Optional.empty();
    }

    public Optional<String> normalizeObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")
                || trimmed.startsWith("\\")
                || WINDOWS_ABSOLUTE_PATH.matcher(trimmed).matches()
                || lower.startsWith("file://")
                || trimmed.contains("\\")
                || trimmed.contains("://")) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    public Optional<String> extractS3HttpObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || host == null) {
                return Optional.empty();
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                return Optional.empty();
            }

            String key;
            if (VIRTUAL_HOSTED_S3.matcher(normalizedHost).matches()) {
                key = path.substring(1);
            } else if (PATH_STYLE_S3.matcher(normalizedHost).matches()) {
                int keyStart = path.indexOf('/', 1);
                if (keyStart < 0 || keyStart + 1 >= path.length()) {
                    return Optional.empty();
                }
                key = path.substring(keyStart + 1);
            } else {
                return Optional.empty();
            }
            return normalizeObjectKey(key);
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern VIRTUAL_HOSTED_S3 =
            Pattern.compile("^.+\\.s3(?:[.-][a-z0-9-]+)?\\.amazonaws\\.com(?:\\.cn)?$");
    private static final Pattern PATH_STYLE_S3 =
            Pattern.compile("^s3(?:[.-][a-z0-9-]+)?\\.amazonaws\\.com(?:\\.cn)?$");

    public record VlmSource(VlmSourceType sourceType, String sourceKey) {
    }
}
