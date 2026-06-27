package com.strange.safety.camera.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.facility.entity.Facility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OverlayRelayServiceTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private CorporateCameraRepository corporateCameraRepository;

    @Mock
    private OverlayBroadcastService broadcastService;

    private MutableClock clock;
    private OverlayRelayService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(10_000L);
        service = new OverlayRelayService(
                cameraRepository,
                corporateCameraRepository,
                broadcastService,
                clock,
                2_000L);
        lenient().when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("cam_03", CameraStatus.ACTIVE))
                .thenReturn(Optional.of(cameraWithFacility(4L)));
    }

    @Test
    void publishesOnlyLatestMessageOnScheduledTick() {
        service.accept(message(100L, "cam_03", "cam_03", List.of(event(new BoundingBox(10, 20, 30, 40)))));
        service.accept(message(101L, "cam_03", "cam_03", List.of(event(new BoundingBox(11, 21, 30, 40)))));

        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService).broadcast(captor.capture(), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(false));
        assertThat(captor.getValue().timestampMs()).isEqualTo(101L);
        assertThat(captor.getValue().cameraLoginId()).isEqualTo("cam_03");
        assertThat(captor.getValue().events().getFirst().boundingBox().x()).isEqualTo(11);
    }

    @Test
    void usesCameraLoginIdBeforeStreamId() {
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("cam_04", CameraStatus.ACTIVE))
                .thenReturn(Optional.of(cameraWithFacility(5L)));

        assertThat(service.accept(message(100L, "edge-internal", "cam_04", List.of(event(validBox()))))).isTrue();
        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService).broadcast(captor.capture(), org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(false));
        assertThat(captor.getValue().streamId()).isEqualTo("edge-internal");
        assertThat(captor.getValue().cameraLoginId()).isEqualTo("cam_04");
    }

    @Test
    void fallsBackToStreamIdWhenCameraLoginIdIsMissing() {
        assertThat(service.accept(message(100L, "cam_03", null, List.of(event(validBox()))))).isTrue();
        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService).broadcast(captor.capture(), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(false));
        assertThat(captor.getValue().cameraLoginId()).isEqualTo("cam_03");
    }

    @Test
    void matchesCorporateCameraWhenFacilityCameraIsMissing() {
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("corp_01", CameraStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("corp_01", CameraStatus.ACTIVE))
                .thenReturn(Optional.of(corporateCameraWithCompanyProfile(9L)));

        assertThat(service.accept(message(100L, "corp_01", "corp_01", List.of(event(validBox()))))).isTrue();
        service.publishLatest();

        verify(broadcastService).broadcast(any(OverlayMessage.class), org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void rejectsEqualAndOlderTimestamps() {
        assertThat(service.accept(message(100L, "cam_03", "cam_03", List.of(event(validBox()))))).isTrue();
        assertThat(service.accept(message(100L, "cam_03", "cam_03", List.of(event(validBox()))))).isFalse();
        assertThat(service.accept(message(99L, "cam_03", "cam_03", List.of(event(validBox()))))).isFalse();

        service.publishLatest();

        verify(broadcastService, times(1)).broadcast(any(OverlayMessage.class), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void rejectsInvalidHeaderAndOutOfFrameBoundingBox() {
        OverlayMessage invalidSchema = new OverlayMessage(
                "2.0", "overlay", 100L, "cam_03", "cam_03", 1280, 720, List.of(event(validBox())));
        OverlayMessage invalidBox = message(
                101L, "cam_03", "cam_03", List.of(event(new BoundingBox(1200, 700, 100, 30))));

        assertThat(service.accept(invalidSchema)).isFalse();
        assertThat(service.accept(invalidBox)).isFalse();

        verify(broadcastService, never()).broadcast(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void acceptsEmptyEventsAsClearPayload() {
        assertThat(service.accept(message(100L, "cam_03", "cam_03", List.of()))).isTrue();

        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService).broadcast(captor.capture(), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(false));
        assertThat(captor.getValue().events()).isEmpty();
    }

    @Test
    void sendsEmptyEventsOnceAfterConfiguredStaleTimeoutWithoutNewData() {
        service.accept(message(100L, "cam_03", "cam_03", List.of(event(validBox()))));
        service.publishLatest();

        clock.advanceMillis(2_000L);
        service.publishLatest();
        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService, times(2)).broadcast(captor.capture(), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(false));
        OverlayMessage cleared = captor.getAllValues().get(1);
        assertThat(cleared.timestampMs()).isEqualTo(12_000L);
        assertThat(cleared.events()).isEmpty();
    }

    @Test
    void acceptsNewSourceTimestampAfterSyntheticClear() {
        service.accept(message(100L, "cam_03", "cam_03", List.of(event(validBox()))));
        clock.advanceMillis(2_000L);
        service.publishLatest();

        assertThat(service.accept(message(101L, "cam_03", "cam_03", List.of(event(validBox()))))).isTrue();
    }

    @Test
    void rejectsUnknownCamera() {
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("unknown", CameraStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("unknown", CameraStatus.ACTIVE))
                .thenReturn(Optional.empty());
        OverlayMessage message = message(100L, "unknown", "unknown", List.of(event(validBox())));

        assertThat(service.accept(message)).isFalse();
        verify(broadcastService, never()).broadcast(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private OverlayMessage message(long timestampMs, String streamId, String cameraLoginId, List<OverlayEvent> events) {
        return new OverlayMessage("1.0", "overlay", timestampMs, streamId, cameraLoginId, 1280, 720, events);
    }

    private OverlayEvent event(BoundingBox box) {
        return new OverlayEvent("FALL_DETECTED", 0.92, 7L, box);
    }

    private BoundingBox validBox() {
        return new BoundingBox(420, 250, 180, 320);
    }

    private Camera cameraWithFacility(Long facilityId) {
        Facility facility = Facility.builder()
                .facilityName("facility")
                .address("address")
                .build();
        ReflectionTestUtils.setField(facility, "id", facilityId);
        Camera camera = Camera.builder()
                .facility(facility)
                .cameraLoginId("cam_03")
                .build();
        return camera;
    }

    private CorporateCamera corporateCameraWithCompanyProfile(Long companyProfileId) {
        CompanyProfile companyProfile = CompanyProfile.builder()
                .companyName("company")
                .build();
        ReflectionTestUtils.setField(companyProfile, "id", companyProfileId);
        return CorporateCamera.builder()
                .companyProfile(companyProfile)
                .cameraLoginId("corp_01")
                .build();
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        void advanceMillis(long amount) {
            millis += amount;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
