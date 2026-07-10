package com.strange.safety.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class AlertBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void broadcastPublishesRealtimePayloadWithPhaseFlagsAndTimestamps() {
        AlertBroadcastService service = new AlertBroadcastService(messagingTemplate);
        SafetyEventDto event = event("realtime", null);

        service.broadcast(4L, false, event);

        ArgumentCaptor<SafetyEventDto> captor = ArgumentCaptor.forClass(SafetyEventDto.class);
        verify(messagingTemplate).convertAndSend("/topic/facility/4/alerts", captor.capture());

        SafetyEventDto published = captor.getValue();
        assertThat(published.eventId()).isEqualTo("evt-1");
        assertThat(published.frameId()).isEqualTo("frame-1");
        assertThat(published.eventPhase()).isEqualTo("realtime");
        assertThat(published.isRealtimeEvent()).isTrue();
        assertThat(published.isEvidenceEvent()).isFalse();
        assertThat(published.capturedAtMs()).isEqualTo(1783410059000L);
        assertThat(published.processedAtMs()).isEqualTo(1783410062400L);
        assertThat(published.mqttPublishedAtMs()).isEqualTo(1783410062500L);
        assertThat(published.mqttReceivedAtMs()).isEqualTo(1783410062600L);
        assertThat(published.publishedAtMs()).isNotNull();
        assertThat(published.clipUrl()).isNull();
        assertThat(published.clipPath()).isNull();
    }

    @Test
    void broadcastPayloadExposesEvidenceFlagWhenEvidenceEventIsPublishedDirectly() {
        AlertBroadcastService service = new AlertBroadcastService(messagingTemplate);
        SafetyEventDto event = event("evidence", "https://example.com/clip.mp4");

        service.broadcast(9L, true, event);

        ArgumentCaptor<SafetyEventDto> captor = ArgumentCaptor.forClass(SafetyEventDto.class);
        verify(messagingTemplate).convertAndSend("/topic/company/9/alerts", captor.capture());

        SafetyEventDto published = captor.getValue();
        assertThat(published.eventPhase()).isEqualTo("evidence");
        assertThat(published.isRealtimeEvent()).isFalse();
        assertThat(published.isEvidenceEvent()).isTrue();
        assertThat(published.clipUrl()).isEqualTo("https://example.com/clip.mp4");
        assertThat(published.publishedAtMs()).isNotNull();
    }

    private SafetyEventDto event(String eventPhase, String clipUrl) {
        return new SafetyEventDto(
                "event",
                eventPhase,
                "frame-1",
                "faint",
                null,
                "cam_05",
                "evt-1",
                "2026-07-09T00:00:00Z",
                "HIGH",
                "AI safety event detected",
                null,
                0.9f,
                null,
                null,
                "track-1",
                clipUrl == null ? null : "clips/test.mp4",
                clipUrl,
                1783410059000L,
                1783410062400L,
                1783410062500L,
                1783410062500L,
                null,
                1783410062600L
        );
    }
}
