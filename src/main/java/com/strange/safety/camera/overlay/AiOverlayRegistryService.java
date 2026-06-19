package com.strange.safety.camera.overlay;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiOverlayRegistryService {

    private static final Pattern LEGACY_CAMERA_ID_PATTERN = Pattern.compile("^cam_?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final int OVERLAY_CONNECT_TIMEOUT_MILLIS = 200;

    private final Clock clock;
    private final ConcurrentMap<String, AiOverlayResponse> overlays = new ConcurrentHashMap<>();

    public AiOverlayRegistryService() {
        this(Clock.systemUTC());
    }

    AiOverlayRegistryService(Clock clock) {
        this.clock = clock;
    }

    public AiOverlayResponse get(String cameraLoginId) {
        String normalizedCameraLoginId = normalizeCameraLoginId(cameraLoginId);
        return overlays.getOrDefault(normalizedCameraLoginId, unknown(normalizedCameraLoginId));
    }

    public AiOverlayResponse requestStart(String cameraLoginId) {
        String normalizedCameraLoginId = normalizeCameraLoginId(cameraLoginId);
        return overlays.compute(normalizedCameraLoginId, (key, existing) -> {
            if (isReusable(existing)) {
                return existing;
            }
            Instant now = clock.instant();
            if (existing == null) {
                return new AiOverlayResponse(key, null, null, null, null, AiOverlayStatus.STARTING, now);
            }
            return new AiOverlayResponse(
                    key,
                    existing.rtspUrl(),
                    existing.overlayPort(),
                    existing.overlayUrl(),
                    existing.pid(),
                    AiOverlayStatus.STARTING,
                    now);
        });
    }

    public AiOverlayResponse stop(String cameraLoginId) {
        String normalizedCameraLoginId = normalizeCameraLoginId(cameraLoginId);
        return overlays.compute(normalizedCameraLoginId, (key, existing) -> {
            Instant now = clock.instant();
            if (existing == null) {
                return new AiOverlayResponse(key, null, null, null, null, AiOverlayStatus.STOPPED, now);
            }
            return new AiOverlayResponse(
                    key,
                    existing.rtspUrl(),
                    existing.overlayPort(),
                    existing.overlayUrl(),
                    existing.pid(),
                    AiOverlayStatus.STOPPED,
                    now);
        });
    }

    public AiOverlayResponse report(AiOverlayReportRequest request) {
        String normalizedCameraLoginId = normalizeCameraLoginId(request.cameraLoginId());
        AiOverlayStatus status = request.status() == null ? AiOverlayStatus.UNKNOWN : request.status();
        AiOverlayResponse response = new AiOverlayResponse(
                normalizedCameraLoginId,
                request.rtspUrl(),
                request.overlayPort(),
                request.overlayUrl(),
                request.pid(),
                status,
                clock.instant());
        overlays.put(normalizedCameraLoginId, response);
        return response;
    }

    public static String normalizeCameraLoginId(String cameraLoginId) {
        String trimmed = cameraLoginId == null ? "" : cameraLoginId.trim();
        Matcher matcher = LEGACY_CAMERA_ID_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        int number = Integer.parseInt(matcher.group(1));
        return "cam_%02d".formatted(number).toLowerCase(Locale.ROOT);
    }

    private AiOverlayResponse unknown(String cameraLoginId) {
        return new AiOverlayResponse(cameraLoginId, null, null, null, null, AiOverlayStatus.UNKNOWN, clock.instant());
    }

    private boolean isReusable(AiOverlayResponse response) {
        if (response == null) {
            return false;
        }
        if (response.status() == AiOverlayStatus.STARTING) {
            return true;
        }
        return response.status() == AiOverlayStatus.RUNNING && isOverlayPortOpen(response);
    }

    private boolean isOverlayPortOpen(AiOverlayResponse response) {
        Integer fallbackPort = response.overlayPort();
        if (fallbackPort == null && response.overlayUrl() == null) {
            return false;
        }
        String host = "localhost";
        int port = fallbackPort == null ? -1 : fallbackPort;
        if (response.overlayUrl() != null) {
            try {
                URI uri = new URI(response.overlayUrl());
                if (uri.getHost() != null) {
                    host = uri.getHost();
                }
                if (uri.getPort() > 0) {
                    port = uri.getPort();
                }
            } catch (URISyntaxException ignored) {
                return false;
            }
        }
        if (port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), OVERLAY_CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
