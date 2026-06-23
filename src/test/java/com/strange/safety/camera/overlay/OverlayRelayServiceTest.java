package com.strange.safety.camera.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
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
                clock);
        lenient().when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("cam_03", CameraStatus.ACTIVE))
                .thenReturn(Optional.of(mock(Camera.class)));
    }

    @Test
    void publishesOnlyLatestMessageOnScheduledTick() {
        service.accept(message(100L, List.of(event(new BoundingBox(10, 20, 30, 40)))));
        service.accept(message(101L, List.of(event(new BoundingBox(11, 21, 30, 40)))));

        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService).broadcast(captor.capture());
        assertThat(captor.getValue().timestampMs()).isEqualTo(101L);
        assertThat(captor.getValue().events().getFirst().boundingBox().x()).isEqualTo(11);
    }

    @Test
    void rejectsEqualAndOlderTimestamps() {
        assertThat(service.accept(message(100L, List.of(event(validBox()))))).isTrue();
        assertThat(service.accept(message(100L, List.of(event(validBox()))))).isFalse();
        assertThat(service.accept(message(99L, List.of(event(validBox()))))).isFalse();

        service.publishLatest();

        verify(broadcastService, times(1)).broadcast(message(100L, List.of(event(validBox()))));
    }

    @Test
    void rejectsInvalidHeaderAndOutOfFrameBoundingBox() {
        OverlayMessage invalidSchema = new OverlayMessage(
                "2.0", "overlay", 100L, "cam_03", 1280, 720, List.of(event(validBox())));
        OverlayMessage invalidBox = message(
                101L, List.of(event(new BoundingBox(1200, 700, 100, 30))));

        assertThat(service.accept(invalidSchema)).isFalse();
        assertThat(service.accept(invalidBox)).isFalse();

        verify(broadcastService, never()).broadcast(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendsEmptyEventsOnceAfterOneSecondWithoutNewData() {
        service.accept(message(100L, List.of(event(validBox()))));
        service.publishLatest();

        clock.advanceMillis(1_000L);
        service.publishLatest();
        service.publishLatest();

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(broadcastService, times(2)).broadcast(captor.capture());
        OverlayMessage cleared = captor.getAllValues().get(1);
        assertThat(cleared.timestampMs()).isEqualTo(11_000L);
        assertThat(cleared.events()).isEmpty();
    }

    @Test
    void acceptsNewSourceTimestampAfterSyntheticClear() {
        service.accept(message(100L, List.of(event(validBox()))));
        clock.advanceMillis(1_000L);
        service.publishLatest();

        assertThat(service.accept(message(101L, List.of(event(validBox()))))).isTrue();
    }

    @Test
    void rejectsUnknownCamera() {
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("unknown", CameraStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc("unknown", CameraStatus.ACTIVE))
                .thenReturn(Optional.empty());
        OverlayMessage message = new OverlayMessage(
                "1.0", "overlay", 100L, "unknown", 1280, 720, List.of(event(validBox())));

        assertThat(service.accept(message)).isFalse();
    }

    private OverlayMessage message(long timestampMs, List<OverlayEvent> events) {
        return new OverlayMessage("1.0", "overlay", timestampMs, "cam_03", 1280, 720, events);
    }

    private OverlayEvent event(BoundingBox box) {
        return new OverlayEvent("FALL_DETECTED", 0.92, 7L, box);
    }

    private BoundingBox validBox() {
        return new BoundingBox(420, 250, 180, 320);
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
