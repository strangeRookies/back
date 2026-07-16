package com.strange.safety.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.strange.safety.scenario.entity.ScenarioType;

@ExtendWith(MockitoExtension.class)
class AlertBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void broadcastPublishesRealtimePayloadWithPhaseFlagsAndTimestamps() throws Exception {
        AlertBroadcastService service = new AlertBroadcastService(messagingTemplate);
        SafetyEventDto event = event("realtime", null);

        service.broadcast(4L, false, event, ScenarioType.COLLAPSE, "쓰러짐 감지");

        ArgumentCaptor<SafetyAlertBroadcastPayload> captor = ArgumentCaptor.forClass(SafetyAlertBroadcastPayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/facility/4/alerts"), captor.capture());

        SafetyAlertBroadcastPayload payload = captor.getValue();
        SafetyEventDto published = payload.event();
        assertThat(payload.scenarioType()).isEqualTo("COLLAPSE");
        assertThat(payload.message()).isEqualTo("쓰러짐 감지");
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

        String json = new ObjectMapper().writeValueAsString(payload);
        assertThat(json).contains("\"eventId\":\"evt-1\"");
        assertThat(json).contains("\"scenarioType\":\"COLLAPSE\"");
        assertThat(json).contains("\"message\":\"쓰러짐 감지\"");
        assertThat(json).doesNotContain("AI safety event detected");
        assertThat(json).doesNotContain("\"event\":");
    }

    @Test
    void broadcastPayloadExposesEvidenceFlagWhenEvidenceEventIsPublishedDirectly() {
        AlertBroadcastService service = new AlertBroadcastService(messagingTemplate);
        SafetyEventDto event = event("evidence", "https://example.com/clip.mp4");

        service.broadcast(9L, true, event, ScenarioType.SYNCOPE, "실신(미회복) 감지");

        ArgumentCaptor<SafetyAlertBroadcastPayload> captor = ArgumentCaptor.forClass(SafetyAlertBroadcastPayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/company/9/alerts"), captor.capture());

        SafetyAlertBroadcastPayload payload = captor.getValue();
        SafetyEventDto published = payload.event();
        assertThat(payload.scenarioType()).isEqualTo("SYNCOPE");
        assertThat(payload.message()).isEqualTo("실신(미회복) 감지");
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
                null,
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
