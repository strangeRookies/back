package com.strange.safety.vlm.snapshotassist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
                "env-injected-gemini-key-for-tests"
        );

        SnapshotAssistStore store = new SnapshotAssistStore(props);
        service = new SnapshotAssistService(props, store, broadcast);
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
        SnapshotAssistRecord first = service.submit("evt-1", "cam_01", jpeg);
        assertThat(first.status()).isEqualTo(SnapshotAssistStatus.PENDING);

        SnapshotAssistRecord analyzed = service.analyzeNow("evt-1");
        assertThat(analyzed.status()).isEqualTo(SnapshotAssistStatus.SUCCESS);
        assertThat(analyzed.summaryKo()).isNotBlank();

        // duplicate submit is idempotent — keeps terminal SUCCESS
        SnapshotAssistRecord again = service.submit("evt-1", "cam_01", jpeg);
        assertThat(again.status()).isEqualTo(SnapshotAssistStatus.SUCCESS);
    }

    @Test
    void missingGeminiKeyRecordsFailedWithoutThrowingToCallerPath() throws Exception {
        SnapshotAssistProperties props = new SnapshotAssistProperties(
                true, "tok", tempDir.resolve("nokey").toString(), 7, false, ""
        );
        SnapshotAssistStore store = new SnapshotAssistStore(props);
        SnapshotAssistService noKey = new SnapshotAssistService(props, store, broadcast);
        noKey.submit("evt-nokey", "cam_x", minimalJpeg());
        SnapshotAssistRecord result = noKey.analyzeNow("evt-nokey");
        assertThat(result.status()).isEqualTo(SnapshotAssistStatus.FAILED);
        assertThat(result.errorMessage()).contains("gemini_key_missing");
    }

    @Test
    void fakeApiKeyBlocked() throws Exception {
        SnapshotAssistProperties props = new SnapshotAssistProperties(
                true, "tok", tempDir.resolve("fake").toString(), 7, false, "fake-api-key"
        );
        SnapshotAssistStore store = new SnapshotAssistStore(props);
        SnapshotAssistService svc = new SnapshotAssistService(props, store, broadcast);
        svc.submit("evt-fake", "cam_x", minimalJpeg());
        assertThat(svc.analyzeNow("evt-fake").errorMessage()).contains("fake_api_key_blocked");
    }

    @Test
    void syntheticNonJpegBlocked() throws Exception {
        SnapshotAssistRecord rec = service.submit("evt-syn", "cam", new byte[]{1, 2, 3});
        assertThat(rec.status()).isEqualTo(SnapshotAssistStatus.FAILED);
        assertThat(rec.errorMessage()).contains("synthetic");
    }

    @Test
    void broadcastReceivesAssistMessageNotAlert() throws Exception {
        service.submit("evt-bc", "cam_01", minimalJpeg());
        service.analyzeNow("evt-bc");
        ArgumentCaptor<SnapshotAssistResultMessage> cap = ArgumentCaptor.forClass(SnapshotAssistResultMessage.class);
        verify(broadcast, atLeastOnce()).publish(cap.capture());
        assertThat(cap.getValue().messageType()).isEqualTo(SnapshotAssistResultMessage.MESSAGE_TYPE);
        assertThat(cap.getValue().eventId()).isEqualTo("evt-bc");
    }

    /** Minimal JPEG SOI/EOI wrapper (not a real image but passes SOI check). */
    private static byte[] minimalJpeg() {
        byte[] bytes = new byte[128];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[126] = (byte) 0xFF;
        bytes[127] = (byte) 0xD9;
        return bytes;
    }
}
