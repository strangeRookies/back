package com.strange.safety.vlm.snapshotassist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SnapshotAssistServiceTest {

    @TempDir
    Path tempDir;

    private SnapshotAssistBroadcastService broadcast;
    private SnapshotAssistService service;

    @BeforeEach
    void setUp() throws Exception {
        broadcast = mock(SnapshotAssistBroadcastService.class);
        SnapshotAssistProperties props = new SnapshotAssistProperties(
                true,
                "test-service-token",
                tempDir.toString(),
                7,
                true,
                false,
                "env-injected-gemini-key-for-tests",
                "gemini-2.0-flash"
        );
        SnapshotAssistStore store = new SnapshotAssistStore(props);
        SnapshotAssistAnalyzer analyzer = new SnapshotAssistAnalyzer(
                props, store, broadcast, new GeminiSnapshotAssistClient(
                new com.fasterxml.jackson.databind.ObjectMapper()));
        service = new SnapshotAssistService(props, store, analyzer);
    }

    @Test
    void rejectMissingServiceToken() {
        assertThat(service.isServiceTokenValid(null)).isFalse();
        assertThat(service.isServiceTokenValid("wrong")).isFalse();
        assertThat(service.isServiceTokenValid("test-service-token")).isTrue();
    }

    @Test
    void submitStoresPendingThenSuccessWithMockAnalyze() throws Exception {
        byte[] jpeg = minimalJpeg();
        SnapshotAssistRecord first = service.submit("evt-1", "cam_01", jpeg, null);
        assertThat(first.status()).isEqualTo(SnapshotAssistStatus.PENDING);

        SnapshotAssistRecord analyzed = service.analyzeNow("evt-1");
        assertThat(analyzed.status()).isEqualTo(SnapshotAssistStatus.SUCCESS);
        assertThat(analyzed.summaryKo()).isNotBlank();

        SnapshotAssistRecord again = service.submit("evt-1", "cam_01", jpeg, null);
        assertThat(again.status()).isEqualTo(SnapshotAssistStatus.SUCCESS);
    }

    @Test
    void missingGeminiKeyRecordsFailedWithoutThrowingToCallerPath() throws Exception {
        SnapshotAssistProperties props = new SnapshotAssistProperties(
                true, "tok", tempDir.resolve("nokey").toString(), 7, false, false, "", "gemini-2.0-flash"
        );
        SnapshotAssistStore store = new SnapshotAssistStore(props);
        SnapshotAssistAnalyzer analyzer = new SnapshotAssistAnalyzer(
                props, store, broadcast, new GeminiSnapshotAssistClient(
                new com.fasterxml.jackson.databind.ObjectMapper()));
        SnapshotAssistService noKey = new SnapshotAssistService(props, store, analyzer);
        noKey.submit("evt-nokey", "cam_x", minimalJpeg(), null);
        SnapshotAssistRecord result = noKey.analyzeNow("evt-nokey");
        assertThat(result.status()).isEqualTo(SnapshotAssistStatus.FAILED);
        assertThat(result.errorMessage()).contains("gemini_key_missing");
    }

    @Test
    void fakeApiKeyBlocked() throws Exception {
        SnapshotAssistProperties props = new SnapshotAssistProperties(
                true, "tok", tempDir.resolve("fake").toString(), 7, false, false, "fake-api-key", "gemini-2.0-flash"
        );
        SnapshotAssistStore store = new SnapshotAssistStore(props);
        SnapshotAssistAnalyzer analyzer = new SnapshotAssistAnalyzer(
                props, store, broadcast, new GeminiSnapshotAssistClient(
                new com.fasterxml.jackson.databind.ObjectMapper()));
        SnapshotAssistService svc = new SnapshotAssistService(props, store, analyzer);
        svc.submit("evt-fake", "cam_x", minimalJpeg(), null);
        assertThat(svc.analyzeNow("evt-fake").errorMessage()).contains("fake_api_key_blocked");
    }

    private static byte[] minimalJpeg() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
                (byte) 0xFF, (byte) 0xD9
        };
    }
}