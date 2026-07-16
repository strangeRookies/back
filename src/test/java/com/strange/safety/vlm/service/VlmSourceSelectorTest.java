package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.vlm.entity.VlmSourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VlmSourceSelectorTest {
    private final VlmSourceSelector selector = new VlmSourceSelector();

    @Test
    void prefersClipObjectKeyThenS3HttpClipUrl() {
        AlertEvent event = event("clips/from-object-key.mp4",
                "https://media-bucket.s3.ap-northeast-2.amazonaws.com/clips/from-url.mp4",
                "C:\\local\\clip.mp4");
        event.getSnapshots().add(snapshot(event, "snapshots/frame.jpg"));

        assertThat(selector.select(event)).contains(
                new VlmSourceSelector.VlmSource(VlmSourceType.CLIP, "clips/from-object-key.mp4"));

        AlertEvent urlEvent = event(null,
                "https://media-bucket.s3.ap-northeast-2.amazonaws.com/clips/from-url.mp4",
                "/var/media/clip.mp4");
        urlEvent.getSnapshots().add(snapshot(urlEvent, "snapshots/frame.jpg"));
        assertThat(selector.select(urlEvent)).contains(
                new VlmSourceSelector.VlmSource(VlmSourceType.CLIP, "clips/from-url.mp4"));

        AlertEvent snapshotOnly = event(null, "https://cdn.example.com/clips/not-s3.mp4", "clips/ignored.mp4");
        snapshotOnly.getSnapshots().add(snapshot(snapshotOnly, "snapshots/frame.jpg"));
        assertThat(selector.select(snapshotOnly)).isEmpty();
    }

    @Test
    void extractsVirtualHostedAndPathStyleS3HttpUrlsOnly() {
        assertThat(selector.extractS3HttpObjectKey(
                "https://bucket.s3.amazonaws.com/clips/a.mp4?versionId=1")).contains("clips/a.mp4");
        assertThat(selector.extractS3HttpObjectKey(
                "http://s3.ap-northeast-2.amazonaws.com/bucket/clips/b.mp4")).contains("clips/b.mp4");
        assertThat(selector.extractS3HttpObjectKey("https://example.com/clips/a.mp4")).isEmpty();
        assertThat(selector.extractS3HttpObjectKey("s3://bucket/clips/a.mp4")).isEmpty();
    }

    @Test
    void rejectsLocalAndFileObjectKeyInputs() {
        assertThat(selector.normalizeObjectKey("C:\\recordings\\clip.mp4")).isEmpty();
        assertThat(selector.normalizeObjectKey("D:/recordings/clip.mp4")).isEmpty();
        assertThat(selector.normalizeObjectKey("/var/recordings/clip.mp4")).isEmpty();
        assertThat(selector.normalizeObjectKey("file:///var/recordings/clip.mp4")).isEmpty();
        assertThat(selector.normalizeObjectKey("clips/valid.mp4")).contains("clips/valid.mp4");
    }

    private AlertEvent event(String clipObjectKey, String clipUrl, String clipPath) {
        return AlertEvent.builder()
                .eventId("event-1")
                .clipObjectKey(clipObjectKey)
                .clipUrl(clipUrl)
                .clipPath(clipPath)
                .build();
    }

    private Snapshot snapshot(AlertEvent event, String key) {
        return Snapshot.builder().alertEvent(event).snapshotUrl(key).build();
    }
}
