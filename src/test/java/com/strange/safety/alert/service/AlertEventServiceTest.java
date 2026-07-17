package com.strange.safety.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.cache.RecentAlertCacheStore;
import com.strange.safety.alert.dto.AlertEventResponse;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.alert.repository.AlertEventRepository;
import com.strange.safety.alert.repository.SnapshotRepository;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.event.SafetyEventDto;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.FacilityType;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.scenario.repository.ScenarioRepository;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.vlm.service.VlmDescriptionEnqueueService;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertEventServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private FacilityService facilityService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private com.strange.safety.corporatecamera.repository.CorporateCameraRepository corporateCameraRepository;

    @Mock
    private com.strange.safety.company.repository.CompanyProfileRepository companyProfileRepository;

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private RecentAlertCacheStore recentAlertCacheStore;

    @Mock
    private S3Service s3Service;

    @Mock
    private VlmDescriptionEnqueueService vlmDescriptionEnqueueService;

    @Mock
    private AlertEventDescriptionRepository alertEventDescriptionRepository;

    @Mock
    private AlertEventIdempotencyLock eventIdempotencyLock;

    private AlertEventService alertEventService;

    @BeforeEach
    void setUp() {
        alertEventService = new AlertEventService(
                alertEventRepository,
                snapshotRepository,
                facilityService,
                userRepository,
                cameraRepository,
                corporateCameraRepository,
                companyProfileRepository,
                scenarioRepository,
                new ObjectMapper(),
                recentAlertCacheStore,
                s3Service,
                vlmDescriptionEnqueueService,
                alertEventDescriptionRepository,
                eventIdempotencyLock
        );
    }

    @Test
    void createEventCachesAlertAfterPostgresqlSave() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01");

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(recentAlertCacheStore).add("FAC_10", response);
        verify(eventIdempotencyLock).acquire(event.eventId());
    }

    @Test
    void getRecentFallsBackToPostgresqlWhenCacheIsEmpty() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);

        when(recentAlertCacheStore.findRecent("FAC_10")).thenReturn(List.of());
        when(alertEventRepository.findTop100ByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(
                any(), any(LocalDateTime.class))).thenReturn(List.of(savedEvent));

        when(userRepository.findById(1L)).thenReturn(Optional.of(com.strange.safety.user.entity.User.builder()
                .email("test@test.com").passwordHash("pwd").name("User").phoneNumber("010").role(com.strange.safety.auth.entity.Role.INDIVIDUAL).build()));
        List<AlertEventResponse> alerts = alertEventService.getRecent(1L, 10L);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().getAlertEventId()).isEqualTo(40L);
        verify(facilityService).getFacilityWithOwnerCheck(1L, 10L);
    }

    @Test
    void getRecentDoesNotQueryPostgresqlWhenCacheHasAlerts() {
        AlertEventResponse cached = AlertEventResponse.builder()
                .alertEventId(40L)
                .detectedAt(LocalDateTime.now())
                .build();
        when(recentAlertCacheStore.findRecent("FAC_10")).thenReturn(List.of(cached));

        when(userRepository.findById(1L)).thenReturn(Optional.of(com.strange.safety.user.entity.User.builder()
                .email("test@test.com").passwordHash("pwd").name("User").phoneNumber("010").role(com.strange.safety.auth.entity.Role.INDIVIDUAL).build()));
        List<AlertEventResponse> alerts = alertEventService.getRecent(1L, 10L);

        assertThat(alerts).containsExactly(cached);
        verify(alertEventRepository, never())
                .findTop100ByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(any(), any());
    }

    @Test
    void createEventKeepsPostgresqlSaveWhenRecentCacheFails() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01");

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(recentAlertCacheStore).add(any(), any());

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(alertEventRepository).save(any(AlertEvent.class));
    }

    @Test
    void createRealtimeEventDoesNotEnterVlmPath() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01");

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(recentAlertCacheStore).add("FAC_10", response);
        verify(vlmDescriptionEnqueueService, never()).enqueueIfMediaExists(any(AlertEvent.class));
    }

    @Test
    void createEventReturnsExistingEventWhenEventIdWasAlreadyPersisted() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent existingEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.of(existingEvent));

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(cameraRepository, never()).findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any());
        verify(recentAlertCacheStore, never()).add(any(), any());
        verify(vlmDescriptionEnqueueService, never()).enqueueIfMediaExists(any(AlertEvent.class));
    }

    @Test
    void createEventNormalizesEventIdBeforeLockAndLookup() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent existingEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01", "SYNCOPE", "  test-event-id  ");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.of(existingEvent));

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(eventIdempotencyLock).acquire("test-event-id");
        verify(alertEventRepository).findByEventId("test-event-id");
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void createEventRejectsBlankEventIdBeforePersistence() {
        SafetyEventDto event = safetyEvent("cam_01", "SYNCOPE", "   ");

        assertThatThrownBy(() -> alertEventService.createEvent(event))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);

        verify(eventIdempotencyLock, never()).acquire(any());
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void createEventAttachesEvidenceClipToExistingEvent() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent existingEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = evidenceEvent("cam_01");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.of(existingEvent));
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("VLM unavailable"))
                .when(vlmDescriptionEnqueueService).enqueueIfMediaExists(existingEvent);

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        assertThat(existingEvent.getClipUrl()).isEqualTo("clips/test.mp4");
        assertThat(existingEvent.getClipObjectKey()).isEqualTo("clips/test.mp4");
        assertThat(existingEvent.getClipPath()).isEqualTo("clips/test.mp4");
        assertThat(response.getSnapshotUrl()).isNull();
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(snapshotRepository, never()).save(any());
        verify(s3Service, never()).generatePresignedUrl(any());
        verify(recentAlertCacheStore).add("FAC_10", response);
        verify(vlmDescriptionEnqueueService).enqueueIfMediaExists(existingEvent);
    }

    @Test
    void createEventStoresEvidenceFirstEventWithoutRecentAlertCache() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = evidenceEvent("cam_01");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.empty());
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenReturn(List.of());

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        assertThat(response.getSnapshotUrl()).isNull();
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(snapshotRepository, never()).save(any());
        verify(recentAlertCacheStore, never()).add(any(), any());
        verify(vlmDescriptionEnqueueService).enqueueIfMediaExists(savedEvent);
    }

    @Test
    void createEventSavesSnapshotOnlyFromSnapshotObjectKey() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEventWithMedia(
                "cam_01",
                "SYNCOPE",
                "test-event-id",
                null,
                "clips/test.mp4",
                "clips/test.mp4",
                "snapshots/test-event-id.jpg");

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        when(snapshotRepository.existsByAlertEvent_IdAndSnapshotUrl(40L, "snapshots/test-event-id.jpg"))
                .thenReturn(false);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenAnswer(invocation -> List.of(
                Snapshot.builder()
                        .alertEvent(savedEvent)
                        .snapshotUrl("snapshots/test-event-id.jpg")
                        .build()));
        when(s3Service.generatePresignedUrl("snapshots/test-event-id.jpg"))
                .thenReturn("https://signed.example.com/snapshots/test-event-id.jpg");

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getSnapshotUrl()).isEqualTo("https://signed.example.com/snapshots/test-event-id.jpg");
        verify(snapshotRepository).save(any(Snapshot.class));
        verify(s3Service).generatePresignedUrl("snapshots/test-event-id.jpg");
        verify(s3Service, never()).generatePresignedUrl("clips/test.mp4");
    }

    @Test
    void createEventDoesNotSaveSnapshotFromClipOnlyPayload() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEventWithMedia(
                "cam_01",
                "SYNCOPE",
                "test-event-id",
                null,
                "clips/test.mp4",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/clips/test.mp4",
                null);

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenReturn(List.of());

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getSnapshotUrl()).isNull();
        verify(snapshotRepository, never()).save(any());
        verify(s3Service, never()).generatePresignedUrl(any());
    }

    @Test
    void createEventAttachesLateSnapshotObjectKeyToExistingEvent() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent existingEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEventWithMedia(
                "cam_01",
                "SYNCOPE",
                "test-event-id",
                null,
                null,
                null,
                "snapshots/test-event-id.jpg");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.of(existingEvent));
        when(snapshotRepository.existsByAlertEvent_IdAndSnapshotUrl(40L, "snapshots/test-event-id.jpg"))
                .thenReturn(false);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenAnswer(invocation -> List.of(
                Snapshot.builder()
                        .alertEvent(existingEvent)
                        .snapshotUrl("snapshots/test-event-id.jpg")
                        .build()));
        when(s3Service.generatePresignedUrl("snapshots/test-event-id.jpg"))
                .thenReturn("https://signed.example.com/snapshots/test-event-id.jpg");

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getSnapshotUrl()).isEqualTo("https://signed.example.com/snapshots/test-event-id.jpg");
        verify(snapshotRepository).save(any(Snapshot.class));
        verify(vlmDescriptionEnqueueService, never()).enqueueIfMediaExists(existingEvent);
    }

    @Test
    void createEventSkipsDuplicateSnapshotObjectKey() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent existingEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEventWithMedia(
                "cam_01",
                "SYNCOPE",
                "test-event-id",
                null,
                null,
                null,
                "snapshots/test-event-id.jpg");

        when(alertEventRepository.findByEventId("test-event-id")).thenReturn(Optional.of(existingEvent));
        when(snapshotRepository.existsByAlertEvent_IdAndSnapshotUrl(40L, "snapshots/test-event-id.jpg"))
                .thenReturn(true);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenReturn(List.of(
                Snapshot.builder().alertEvent(existingEvent).snapshotUrl("snapshots/test-event-id.jpg").build()));
        when(s3Service.generatePresignedUrl("snapshots/test-event-id.jpg"))
                .thenReturn("https://signed.example.com/snapshots/test-event-id.jpg");

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getSnapshotUrl()).isEqualTo("https://signed.example.com/snapshots/test-event-id.jpg");
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void createEventRejectsClipKeyDisguisedAsSnapshotObjectKey() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEventWithMedia(
                "cam_01",
                "SYNCOPE",
                "test-event-id",
                null,
                null,
                null,
                "clips/test.mp4");

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        when(snapshotRepository.findByAlertEvent_Id(40L)).thenReturn(List.of());

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getSnapshotUrl()).isNull();
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void createEventMapsAiTypesToServiceScenarios() {
        assertMappedScenario("faint", ScenarioType.COLLAPSE);
        assertMappedScenario("FAINT_SUSPECTED", ScenarioType.SYNCOPE);
        assertMappedScenario("FALL_UNRECOVERED", ScenarioType.SYNCOPE);
        assertMappedScenario("fall", ScenarioType.FALL_BED);
        assertMappedScenario("exit", ScenarioType.EXIT);
        assertMappedScenario("hazard", ScenarioType.HAZARD_ZONE);
    }

    @Test
    void displayMessagesMatchServiceScenarios() {
        assertThat(alertEventService.getDisplayMessage(ScenarioType.COLLAPSE)).isEqualTo("쓰러짐 감지");
        assertThat(alertEventService.getDisplayMessage(ScenarioType.SYNCOPE)).isEqualTo("실신(미회복) 감지");
        assertThat(alertEventService.getDisplayMessage(ScenarioType.FALL_BED)).isEqualTo("낙상 감지");
        assertThat(alertEventService.getDisplayMessage(ScenarioType.EXIT)).isEqualTo("이탈 감지");
        assertThat(alertEventService.getDisplayMessage(ScenarioType.HAZARD_ZONE)).isEqualTo("위험구역 진입");
    }

    @Test
    void createEventRejectsUnsupportedAiTypeBeforePersistence() {
        SafetyEventDto event = safetyEvent("cam_01", "UNKNOWN_EVENT");

        assertThatThrownBy(() -> alertEventService.createEvent(event))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);

        verify(cameraRepository, never()).findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any());
        verify(alertEventRepository, never()).save(any());
        verify(recentAlertCacheStore, never()).add(any(), any());
    }

    private SafetyEventDto safetyEvent(String cameraLoginId) {
        return safetyEvent(cameraLoginId, "SYNCOPE", "test-event-id");
    }

    private SafetyEventDto safetyEvent(String cameraLoginId, String type) {
        return safetyEvent(cameraLoginId, type, "test-event-id");
    }

    private SafetyEventDto safetyEvent(String cameraLoginId, String type, String eventId) {
        return new SafetyEventDto(
                null,
                null,
                "frame-1",
                type,
                null,
                cameraLoginId,
                eventId,
                Instant.parse("2026-06-19T00:00:00Z").toString(),
                "CRITICAL",
                "AI safety event detected",
                null,
                0.9f,
                null,
                null,
                "track-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void assertMappedScenario(String type, ScenarioType expectedScenarioType) {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L, expectedScenarioType);
        SafetyEventDto event = safetyEvent("cam_01", type);

        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(
                "cam_01", com.strange.safety.camera.entity.CameraStatus.ACTIVE)).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(expectedScenarioType)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getScenarioType()).isEqualTo(expectedScenarioType.name());
    }

    private SafetyEventDto evidenceEvent(String cameraLoginId) {
        return safetyEventWithMedia(
                cameraLoginId,
                "SYNCOPE",
                "test-event-id",
                "clips/test.mp4",
                "clips/test.mp4",
                "clips/test.mp4",
                null);
    }

    private SafetyEventDto safetyEventWithMedia(
            String cameraLoginId,
            String type,
            String eventId,
            String clipPath,
            String clipObjectKey,
            String clipUrl,
            String snapshotObjectKey) {
        return new SafetyEventDto(
                "event",
                snapshotObjectKey == null && clipObjectKey != null ? "evidence" : null,
                "frame-1",
                type,
                null,
                cameraLoginId,
                eventId,
                Instant.parse("2026-06-19T00:00:00Z").toString(),
                "CRITICAL",
                "AI safety event detected",
                null,
                0.9f,
                null,
                null,
                "track-1",
                clipPath,
                clipObjectKey,
                clipUrl,
                snapshotObjectKey,
                clipObjectKey != null ? 1783410059000L : null,
                clipObjectKey != null ? 1783410062400L : null,
                clipObjectKey != null ? 1783410062500L : null,
                clipObjectKey != null ? 1783410062500L : null,
                null,
                clipObjectKey != null ? 1783410062600L : null,
                null
        );
    }

    private Facility facility(Long id) {
        Facility facility = Facility.builder()
                .facilityName("facility")
                .facilityType(FacilityType.HOME)
                .address("address")
                .build();
        ReflectionTestUtils.setField(facility, "id", id);
        return facility;
    }

    private Camera camera(Long id, Facility facility) {
        Camera camera = Camera.builder()
                .facility(facility)
                .cameraLoginId("cam_01")
                .cameraName("camera")
                .build();
        ReflectionTestUtils.setField(camera, "id", id);
        return camera;
    }

    private Scenario scenario(Long id) {
        return scenario(id, ScenarioType.SYNCOPE);
    }

    private Scenario scenario(Long id, ScenarioType scenarioType) {
        Scenario scenario = Scenario.builder()
                .scenarioType(scenarioType)
                .description("syncope")
                .build();
        ReflectionTestUtils.setField(scenario, "id", id);
        return scenario;
    }

    private AlertEvent alertEvent(Long id, Camera camera, Scenario scenario) {
        AlertEvent event = AlertEvent.builder()
                .camera(camera)
                .scenario(scenario)
                .confidenceScore(0.9f)
                .severity(com.strange.safety.alert.entity.AlertSeverity.CRITICAL)
                .keypointData("message")
                .detectedAt(LocalDateTime.of(2026, 6, 19, 0, 0))
                .eventId("test-event-id")
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
