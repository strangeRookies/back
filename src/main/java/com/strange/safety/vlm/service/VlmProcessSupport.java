package com.strange.safety.vlm.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Pure helpers for VLM process gating — unit-testable without Spring / ProcessBuilder.
 */
public final class VlmProcessSupport {

    private VlmProcessSupport() {
    }

    /** Empty if script path is a regular file; else failure message for job.markFailed. */
    public static Optional<String> scriptMissingMessage(Path scriptPath) {
        if (scriptPath != null && Files.isRegularFile(scriptPath)) {
            return Optional.empty();
        }
        return Optional.of("VLM process script not found: " + scriptPath);
    }

    /** Empty if process finished within wait; else timeout failure message. */
    public static Optional<String> timeoutMessage(boolean finished, long timeoutSeconds) {
        if (finished) {
            return Optional.empty();
        }
        return Optional.of("VLM process timed out after " + timeoutSeconds + " seconds");
    }

    /**
     * Whether the job is still within its retry budget (uses job.maxRetries set from vlm.max-retry).
     */
    public static boolean hasRetryBudget(int retryCount, int maxRetries) {
        return retryCount <= Math.max(0, maxRetries);
    }
}
