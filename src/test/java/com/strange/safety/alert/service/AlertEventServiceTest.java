package com.strange.safety.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.cache.RecentAlertCacheStore;
import com.strange.safety.alert.dto.AlertEventResponse;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.repository.AlertEventRepository;
import com.strange.safety.alert.repository.SnapshotRepository;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.event.SafetyEventDto;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.FacilityType;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.scenario.repository.ScenarioRepository;
import com.strange.safety.user.repository.UserRepository;
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
    private ScenarioRepository scenarioRepository;

    @Mock
    private RecentAlertCacheStore recentAlertCacheStore;

    private AlertEventService alertEventService;

    @BeforeEach
    void setUp() {
        alertEventService = new AlertEventService(
                alertEventRepository,
                snapshotRepository,
                facilityService,
                userRepository,
                cameraRepository,
                scenarioRepository,
                new ObjectMapper(),
                recentAlertCacheStore
        );
    }

    @Test
    void createEventCachesAlertAfterPostgresqlSave() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);
        SafetyEventDto event = safetyEvent("cam_01");

        when(cameraRepository.findFirstByCameraLoginIdOrderByIdDesc("cam_01")).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(recentAlertCacheStore).add(10L, response);
    }

    @Test
    void getRecentFallsBackToPostgresqlWhenCacheIsEmpty() {
        Facility facility = facility(10L);
        Camera camera = camera(20L, facility);
        Scenario scenario = scenario(30L);
        AlertEvent savedEvent = alertEvent(40L, camera, scenario);

        when(recentAlertCacheStore.findRecent(10L)).thenReturn(List.of());
        when(alertEventRepository.findTop100ByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(
                any(), any(LocalDateTime.class))).thenReturn(List.of(savedEvent));

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
        when(recentAlertCacheStore.findRecent(10L)).thenReturn(List.of(cached));

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

        when(cameraRepository.findFirstByCameraLoginIdOrderByIdDesc("cam_01")).thenReturn(Optional.of(camera));
        when(scenarioRepository.findByScenarioType(ScenarioType.SYNCOPE)).thenReturn(Optional.of(scenario));
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(savedEvent);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(recentAlertCacheStore).add(any(), any());

        AlertEventResponse response = alertEventService.createEvent(event);

        assertThat(response.getAlertEventId()).isEqualTo(40L);
        verify(alertEventRepository).save(any(AlertEvent.class));
    }

    private SafetyEventDto safetyEvent(String cameraLoginId) {
        return new SafetyEventDto(
                null,
                "SYNCOPE",
                null,
                cameraLoginId,
                Instant.parse("2026-06-19T00:00:00Z").toString(),
                "CRITICAL",
                "AI safety event detected",
                null,
                0.9f,
                null,
                null,
                "track-1",
                null,
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
        Scenario scenario = Scenario.builder()
                .scenarioType(ScenarioType.SYNCOPE)
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
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
