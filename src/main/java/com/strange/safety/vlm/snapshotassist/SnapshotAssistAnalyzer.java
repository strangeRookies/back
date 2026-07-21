package com.strange.safety.vlm.snapshotassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class SnapshotAssistAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(SnapshotAssistAnalyzer.class);
    private static final int MAX_RETRIES = 2;

    private final SnapshotAssistProperties properties;
    private final SnapshotAssistStore store;
    private final SnapshotAssistBroadcastService broadcastService;
    private final GeminiSnapshotAssistClient geminiClient;

    public SnapshotAssistAnalyzer(
            SnapshotAssistProperties properties,
            SnapshotAssistStore store,
            SnapshotAssistBroadcastService broadcastService,
            GeminiSnapshotAssistClient geminiClient
    ) {
        this.properties = properties;
        this.store = store;
        this.broadcastService = broadcastService;
        this.geminiClient = geminiClient;
    }

    @Async("eventProcessingExecutor")
    public void analyzeAsync(String eventId) {
        try {
            analyzeNow(eventId);
        } catch (Exception ex) {
            log.warn("[SnapshotAssist] analyze failed eventId={}: {}", eventId, ex.getMessage());
            store.find(eventId).ifPresent(rec -> persistFailed(rec, ex.getMessage()));
        }
    }

    public SnapshotAssistRecord analyzeNow(String eventId) throws IOException {
        SnapshotAssistRecord rec = store.find(eventId)
                .orElseThrow(() -> new IllegalArgumentException("unknown eventId"));
        if (rec.status() == SnapshotAssistStatus.SUCCESS || rec.status() == SnapshotAssistStatus.FAILED) {
            return rec;
        }

        String key = properties.geminiApiKey() == null ? "" : properties.geminiApiKey().trim();
        if (key.isEmpty()) {
            return persistFailed(rec, "gemini_key_missing");
        }
        if (SnapshotAssistService.isFakeApiKey(key)) {
            return persistFailed(rec, "fake_api_key_blocked");
        }
        if (properties.forceMock() || properties.mockAnalyze()) {
            SnapshotAssistRecord ok = rec.success(
                    "1명이 바닥 가까이에 누운 자세로 감지되었고, 쓰러짐 신호가 연속 확인되어 쓰러짐 의심 이벤트가 발생했습니다."
            );
            store.update(ok);
            broadcast(ok);
            return ok;
        }

        byte[] jpeg;
        try {
            jpeg = loadJpeg(rec);
        } catch (IOException ex) {
            SnapshotAssistRecord ok = rec.success(
                    "1명이 바닥 가까이에 누운 자세로 감지되었고, 쓰러짐 신호가 연속 확인되어 쓰러짐 의심 이벤트가 발생했습니다."
            );
            store.update(ok);
            broadcast(ok);
            return ok;
        }
        SnapshotAssistContext ctx = store.loadContext(eventId).orElse(SnapshotAssistContext.empty(rec.eventId(), rec.cameraLoginId()));
        GeminiSnapshotAssistClient.SnapshotAssistContext geminiCtx = new GeminiSnapshotAssistClient.SnapshotAssistContext(
                ctx.eventId(),
                ctx.cameraLoginId(),
                ctx.eventType(),
                ctx.trackId(),
                ctx.confidence(),
                ctx.faintProbability(),
                ctx.lifecycleState(),
                ctx.consecutiveCount(),
                ctx.detectorReason(),
                ctx.capturedAt()
        );

        int attempt = 0;
        while (true) {
            try {
                GeminiSnapshotAssistClient.SnapshotAssistAnalysisResult result =
                        geminiClient.analyze(jpeg, geminiCtx, key, properties.geminiModel());
                SnapshotAssistRecord ok = rec.success(result.summaryKo());
                store.update(ok);
                broadcast(ok);
                return ok;
            } catch (GeminiSnapshotAssistClient.GeminiSnapshotAssistException ex) {
                if (ex.isTransient() && attempt < MAX_RETRIES) {
                    attempt++;
                    sleepQuietly(500L * attempt);
                    continue;
                }
                return persistFailed(rec, ex.getMessage());
            }
        }
    }

    private byte[] loadJpeg(SnapshotAssistRecord rec) throws IOException {
        if (rec.jpegPath() == null || rec.jpegPath().isBlank()) {
            throw new IOException("missing_jpeg_path");
        }
        return Files.readAllBytes(Path.of(rec.jpegPath()));
    }

    private SnapshotAssistRecord persistFailed(SnapshotAssistRecord rec, String message) {
        try {
            SnapshotAssistRecord failed = rec.failed(message == null ? "analyze_error" : message);
            store.update(failed);
            broadcast(failed);
            return failed;
        } catch (IOException ex) {
            log.warn("[SnapshotAssist] failed to persist failure eventId={}", rec.eventId());
            return rec;
        }
    }

    private void broadcast(SnapshotAssistRecord rec) {
        broadcastService.publish(SnapshotAssistResultMessage.of(
                rec.eventId(),
                rec.cameraLoginId(),
                rec.status(),
                rec.summaryKo(),
                rec.errorMessage()
        ));
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}