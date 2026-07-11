package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.vlm.entity.VlmSourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VlmSourceSelectorTest {

    private final VlmSourceSelector selector = new VlmSourceSelector();

    @Test
    void prefersClipUrlOverSnapshot() {
        AlertEvent event = AlertEvent.builder()
                .clipUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/clips/fall_cam.mp4")
                .build();
        attachSnapshot(event, "snapshots/evt-1.jpg");

        Optional<VlmSourceSelector.VlmSource> source = selector.select(event);

        assertThat(source).isPresent();
        assertThat(source.get().sourceType()).isEqualTo(VlmSourceType.CLIP);
        assertThat(source.get().sourceKey()).isEqualTo("clips/fall_cam.mp4");
    }

    @Test
    void rejectsLegacyClipKeyStoredInSnapshotUrl() {
        AlertEvent event = AlertEvent.builder().build();
        attachSnapshot(event, "clips/legacy_event.mp4");

        Optional<VlmSourceSelector.VlmSource> source = selector.select(event);

        assertThat(source).isEmpty();
    }

    @Test
    void acceptsImageSnapshotKey() {
        AlertEvent event = AlertEvent.builder().build();
        attachSnapshot(event, "snapshots/evt-99.jpg");

        Optional<VlmSourceSelector.VlmSource> source = selector.select(event);

        assertThat(source).isPresent();
        assertThat(source.get().sourceType()).isEqualTo(VlmSourceType.SNAPSHOT);
        assertThat(source.get().sourceKey()).isEqualTo("snapshots/evt-99.jpg");
    }

    @Test
    void looksLikeVideoDetectsExtensionsAndClipsPath() {
        assertThat(selector.looksLikeVideo("clips/a.mp4", null)).isTrue();
        assertThat(selector.looksLikeVideo("foo/bar.mov", null)).isTrue();
        assertThat(selector.looksLikeVideo("snapshots/x.jpg", null)).isFalse();
        assertThat(selector.looksLikeVideo("obj", "contentType=video/mp4")).isTrue();
    }

    @Test
    void emptyWhenNoAssets() {
        AlertEvent event = AlertEvent.builder().build();
        assertThat(selector.select(event)).isEmpty();
    }

    private static void attachSnapshot(AlertEvent event, String snapshotUrl) {
        Snapshot snap = Snapshot.builder()
                .alertEvent(event)
                .snapshotUrl(snapshotUrl)
                .fileSizeBytes(1L)
                .build();
        List<Snapshot> list = new ArrayList<>();
        list.add(snap);
        // AlertEvent.getSnapshots() is typically a managed collection — set via reflection if needed
        try {
            var field = AlertEvent.class.getDeclaredField("snapshots");
            field.setAccessible(true);
            field.set(event, list);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
