package com.strange.safety.vlm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VlmProcessSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void scriptMissingWhenPathAbsent() {
        Path missing = tempDir.resolve("no-such-process_vlm.py");
        assertThat(VlmProcessSupport.scriptMissingMessage(missing))
                .isPresent()
                .get()
                .asString()
                .contains("VLM process script not found");
    }

    @Test
    void scriptPresentWhenRegularFile() throws Exception {
        Path script = tempDir.resolve("process_vlm.py");
        Files.writeString(script, "print('ok')\n");
        assertThat(VlmProcessSupport.scriptMissingMessage(script)).isEmpty();
    }

    @Test
    void timeoutMessageWhenNotFinished() {
        assertThat(VlmProcessSupport.timeoutMessage(false, 120))
                .contains("VLM process timed out after 120 seconds");
        assertThat(VlmProcessSupport.timeoutMessage(true, 120)).isEmpty();
    }

    @Test
    void retryBudgetHonorsMaxRetryOne() {
        assertThat(VlmProcessSupport.hasRetryBudget(0, 1)).isTrue();
        assertThat(VlmProcessSupport.hasRetryBudget(1, 1)).isTrue();
        assertThat(VlmProcessSupport.hasRetryBudget(2, 1)).isFalse();
        assertThat(VlmProcessSupport.hasRetryBudget(0, 3)).isTrue();
        assertThat(VlmProcessSupport.hasRetryBudget(3, 3)).isTrue();
        assertThat(VlmProcessSupport.hasRetryBudget(4, 3)).isFalse();
    }
}
