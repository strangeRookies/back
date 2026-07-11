package com.strange.safety.vlm.snapshotassist;

import java.time.Instant;

/**
 * Separate STOMP/MQTT payload for VLM snapshot assist.
 * Never replaces primary safety alert DTOs.
 */
public record SnapshotAssistResultMessage(
        String messageType,
        String eventId,
        String cameraLoginId,
        SnapshotAssistStatus status,
        String summaryKo,
        String errorMessage,
        Instant updatedAt
) {
    public static final String MESSAGE_TYPE = "vlm_snapshot_assist";

    public static SnapshotAssistResultMessage of(
            String eventId,
            String cameraLoginId,
            SnapshotAssistStatus status,
            String summaryKo,
            String errorMessage
    ) {
        return new SnapshotAssistResultMessage(
                MESSAGE_TYPE,
                eventId,
                cameraLoginId,
                status,
                summaryKo,
                errorMessage,
                Instant.now()
        );
    }
}
