package com.strange.safety.camera.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class AiOverlayRegistryServiceTest {

    private final AiOverlayRegistryService registry = new AiOverlayRegistryService(
            Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void requestStartReturnsRunningOverlayWhenAlreadyReported() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            registry.report(new AiOverlayReportRequest(
                    "cam_04",
                    "rtsp://127.0.0.1:8554/cam_04",
                    port,
                    "http://localhost:%d/stream".formatted(port),
                    1234L,
                    AiOverlayStatus.RUNNING));

            AiOverlayResponse response = registry.requestStart("cam4");

            assertThat(response.cameraLoginId()).isEqualTo("cam_04");
            assertThat(response.status()).isEqualTo(AiOverlayStatus.RUNNING);
            assertThat(response.overlayPort()).isEqualTo(port);
            assertThat(response.overlayUrl()).isEqualTo("http://localhost:%d/stream".formatted(port));
        }
    }

    @Test
    void requestStartChangesStaleRunningOverlayToStartingWhenPortIsClosed() {
        registry.report(new AiOverlayReportRequest(
                "cam_04",
                "rtsp://127.0.0.1:8554/cam_04",
                8010,
                "http://localhost:8010/stream",
                1234L,
                AiOverlayStatus.RUNNING));

        AiOverlayResponse response = registry.requestStart("cam4");

        assertThat(response.cameraLoginId()).isEqualTo("cam_04");
        assertThat(response.status()).isEqualTo(AiOverlayStatus.STARTING);
        assertThat(response.overlayPort()).isEqualTo(8010);
        assertThat(response.overlayUrl()).isEqualTo("http://localhost:8010/stream");
    }

    @Test
    void requestStartCreatesStartingStateWhenOverlayIsMissing() {
        AiOverlayResponse response = registry.requestStart("cam_01");

        assertThat(response.cameraLoginId()).isEqualTo("cam_01");
        assertThat(response.status()).isEqualTo(AiOverlayStatus.STARTING);
        assertThat(response.overlayUrl()).isNull();
    }

    @Test
    void reportNormalizesLegacyCameraLoginId() {
        AiOverlayResponse response = registry.report(new AiOverlayReportRequest(
                "cam1",
                "rtsp://127.0.0.1:8554/cam_01",
                8012,
                "http://localhost:8012/stream",
                5678L,
                AiOverlayStatus.RUNNING));

        assertThat(response.cameraLoginId()).isEqualTo("cam_01");
        assertThat(registry.get("cam_01").overlayPort()).isEqualTo(8012);
    }

    @Test
    void reportNormalizesWildcardOverlayUrlForBrowser() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            registry.report(new AiOverlayReportRequest(
                    "cam_03",
                    "rtsp://127.0.0.1:8554/cam_03",
                    port,
                    "http://0.0.0.0:%d/mjpeg/cam_03".formatted(port),
                    5678L,
                    AiOverlayStatus.RUNNING));

            AiOverlayResponse response = registry.getForCameraResponse("cam_03");

            assertThat(response.overlayUrl()).isEqualTo("http://localhost:%d/mjpeg/cam_03".formatted(port));
        }
    }

    @Test
    void getForCameraResponseDisablesOverlayUrlWhenPortIsClosed() throws IOException {
        int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            closedPort = serverSocket.getLocalPort();
        }

        registry.report(new AiOverlayReportRequest(
                "cam_03",
                "rtsp://127.0.0.1:8554/cam_03",
                closedPort,
                "http://localhost:%d/mjpeg/cam_03".formatted(closedPort),
                5678L,
                AiOverlayStatus.RUNNING));

        AiOverlayResponse response = registry.getForCameraResponse("cam_03");

        assertThat(response.overlayUrl()).isNull();
        assertThat(response.overlayPort()).isEqualTo(closedPort);
    }

    @Test
    void reportKeepsAiProvidedCameraPortMapping() {
        registry.report(new AiOverlayReportRequest(
                "cam_02",
                "rtsp://127.0.0.1:8554/cam_02",
                8012,
                "http://localhost:8012/mjpeg/cam_02",
                5678L,
                AiOverlayStatus.RUNNING));

        AiOverlayResponse response = registry.get("cam_02");

        assertThat(response.overlayPort()).isEqualTo(8012);
        assertThat(response.overlayUrl()).isEqualTo("http://localhost:8012/mjpeg/cam_02");
    }
}
