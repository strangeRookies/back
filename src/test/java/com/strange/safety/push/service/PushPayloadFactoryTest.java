package com.strange.safety.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.gateway.PushMessagePayload;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushPayloadFactoryTest {

    private final PushPayloadFactory factory = new PushPayloadFactory();

    @Test
    void buildsAndroidAlertPayloadWithStringData() {
        PushMessagePayload payload = factory.alert(new AlertPushRequestedEvent(
                123L, 30L, "cam_03", "CCTV-03", "FACILITY", 10L,
                "FALL_BED", "CRITICAL", Instant.parse("2026-07-20T05:30:00Z")));

        assertThat(payload.title()).isEqualTo("낙상 위험 감지");
        assertThat(payload.body()).contains("CCTV-03");
        assertThat(payload.data()).containsEntry("type", "AI_DANGER_EVENT")
                .containsEntry("eventId", "123")
                .containsEntry("cameraId", "30")
                .containsEntry("facilityId", "10")
                .containsEntry("targetType", "FACILITY")
                .containsEntry("occurredAt", "2026-07-20T05:30:00Z");
    }

    @Test
    void companyPayloadUsesCompanyProfileId() {
        PushMessagePayload payload = factory.alert(new AlertPushRequestedEvent(
                123L, 30L, "corp-cam", null, "COMPANY", 20L,
                "ASSAULT", "CRITICAL", Instant.parse("2026-07-20T05:30:00Z")));

        assertThat(payload.data()).containsEntry("companyProfileId", "20")
                .doesNotContainKey("facilityId");
    }
}
