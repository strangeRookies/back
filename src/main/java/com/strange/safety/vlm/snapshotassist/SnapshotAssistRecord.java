package com.strange.safety.vlm.snapshotassist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnapshotAssistRecord(
        String eventId,
        String cameraLoginId,
        SnapshotAssistStatus status,
        String jpegPath,
        String summaryKo,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static SnapshotAssistRecord pending(String eventId, String cameraLoginId, String jpegPath) {
        Instant now = Instant.now();
        return new SnapshotAssistRecord(
                eventId,
                cameraLoginId,
                SnapshotAssistStatus.PENDING,
                jpegPath,
                null,
                null,
                now,
                now
        );
    }

    public SnapshotAssistRecord withJpegPath(String path) {
        return new SnapshotAssistRecord(
                eventId, cameraLoginId, status, path, summaryKo, errorMessage, createdAt, Instant.now()
        );
    }

    public SnapshotAssistRecord touch() {
        return new SnapshotAssistRecord(
                eventId, cameraLoginId, status, jpegPath, summaryKo, errorMessage, createdAt, Instant.now()
        );
    }

    public SnapshotAssistRecord success(String summaryKo) {
        return new SnapshotAssistRecord(
                eventId, cameraLoginId, SnapshotAssistStatus.SUCCESS, jpegPath,
                summaryKo, null, createdAt, Instant.now()
        );
    }

    public SnapshotAssistRecord failed(String errorMessage) {
        return new SnapshotAssistRecord(
                eventId, cameraLoginId, SnapshotAssistStatus.FAILED, jpegPath,
                null, errorMessage, createdAt, Instant.now()
        );
    }
}
