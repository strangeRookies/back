package com.strange.safety.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.cache.RecentAlertCacheStore;
import com.strange.safety.alert.dto.AlertEventDetailResponse;
import com.strange.safety.alert.dto.AlertEventResponse;
import com.strange.safety.alert.dto.AlertStatsResponse;
import com.strange.safety.alert.dto.SnapshotResponse;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import com.strange.safety.alert.repository.AlertEventRepository;
import com.strange.safety.alert.repository.SnapshotRepository;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.event.SafetyEventDto;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.scenario.repository.ScenarioRepository;
import com.strange.safety.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertEventService {

    private static final Logger log = LoggerFactory.getLogger(AlertEventService.class);

    private final AlertEventRepository alertEventRepository;
    private final SnapshotRepository snapshotRepository;
    private final FacilityService facilityService;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final ScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper;
    private final RecentAlertCacheStore recentAlertCacheStore;

    public Page<AlertEventResponse> getList(Long userId, Long facilityId,
                                            AlertSeverity severity, AlertStatus status,
                                            LocalDateTime dateFrom, LocalDateTime dateTo,
                                            Pageable pageable) {
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        Specification<AlertEvent> spec = Specification
                .where(facilityEquals(facilityId))
                .and(severity != null ? (r, q, cb) -> cb.equal(r.get("severity"), severity) : null)
                .and(status != null ? (r, q, cb) -> cb.equal(r.get("status"), status) : null)
                .and(dateFrom != null ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("detectedAt"), dateFrom) : null)
                .and(dateTo != null ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("detectedAt"), dateTo) : null);

        return alertEventRepository.findAll(spec, pageable).map(AlertEventResponse::from);
    }

    public AlertEventDetailResponse getDetail(Long userId, Long alertEventId) {
        AlertEvent event = getEventWithOwnerCheck(userId, alertEventId);
        List<SnapshotResponse> snapshots = snapshotRepository.findByAlertEvent_Id(alertEventId)
                .stream().map(SnapshotResponse::from).collect(Collectors.toList());
        return AlertEventDetailResponse.from(event, snapshots);
    }

    @Transactional
    public AlertEventResponse acknowledge(Long userId, Long alertEventId) {
        AlertEvent event = getEventWithOwnerCheck(userId, alertEventId);
        event.acknowledge(userRepository.getReferenceById(userId));
        return AlertEventResponse.from(event);
    }

    public AlertStatsResponse getStats(Long userId, Long facilityId,
                                       LocalDateTime dateFrom, LocalDateTime dateTo) {
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        // null 파라미터 바인딩 오류 방지 — 범위 미지정 시 전체 기간으로 대체
        LocalDateTime from = dateFrom != null ? dateFrom : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to   = dateTo   != null ? dateTo   : LocalDateTime.now().plusYears(10);

        long total     = alertEventRepository.countByFacilityAndDateRange(facilityId, from, to);
        long warning   = alertEventRepository.countByFacilityAndSeverity(facilityId, AlertSeverity.WARNING, from, to);
        long critical  = alertEventRepository.countByFacilityAndSeverity(facilityId, AlertSeverity.CRITICAL, from, to);
        long pending   = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.PENDING, from, to);
        long confirmed = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.CONFIRMED, from, to);
        long dismissed = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.DISMISSED, from, to);

        List<AlertStatsResponse.ScenarioCount> byScenario =
                alertEventRepository.countGroupByScenario(facilityId, from, to).stream()
                        .map(row -> AlertStatsResponse.ScenarioCount.builder()
                                .scenarioType((String) row[0])
                                .count(((Number) row[1]).longValue())
                                .build())
                        .collect(Collectors.toList());

        return AlertStatsResponse.builder()
                .total(total).warning(warning).critical(critical)
                .pending(pending).confirmed(confirmed).dismissed(dismissed)
                .byScenario(byScenario)
                .build();
    }

    public List<AlertEventResponse> getRecent(Long userId, Long facilityId) {
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        List<AlertEventResponse> cachedAlerts = recentAlertCacheStore.findRecent(facilityId);
        if (!cachedAlerts.isEmpty()) {
            return cachedAlerts;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        return alertEventRepository
                .findTop100ByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(facilityId, cutoff)
                .stream()
                .map(AlertEventResponse::from)
                .collect(Collectors.toList());
    }

    private AlertEvent getEventWithOwnerCheck(Long userId, Long alertEventId) {
        AlertEvent event = alertEventRepository.findById(alertEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALERT_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, event.getCamera().getFacility().getId());
        return event;
    }

    private static Specification<AlertEvent> facilityEquals(Long facilityId) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(
                    root.join("camera").join("facility").get("id"),
                    facilityId);
        };
    }

    public long countAllAlertsToday() {
        return alertEventRepository.countAllSince(LocalDateTime.now().minusHours(24));
    }

    @Transactional
    public AlertEventResponse createEvent(SafetyEventDto dto) {
        String cameraIdVal = firstNonBlank(dto.cameraLoginId(), dto.cameraId(), "cam_01");
        
        // Convert "cam1", "cam2" or "CCTV-01" into DB format "cam_01"
        if (cameraIdVal.startsWith("cam") && cameraIdVal.length() == 4) {
            cameraIdVal = "cam_0" + cameraIdVal.charAt(3);
        } else if (cameraIdVal.startsWith("CCTV-0") && cameraIdVal.length() == 7) {
            cameraIdVal = "cam_0" + cameraIdVal.charAt(6);
        }

        String finalCameraIdVal = cameraIdVal;
        Camera camera = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc(finalCameraIdVal)
                .orElseThrow(() -> {
                    log.error("Failed to map MQTT safety event camera: rawCameraLoginId={}, rawCameraId={}, normalizedCameraLoginId={}",
                            dto.cameraLoginId(), dto.cameraId(), finalCameraIdVal);
                    return new CustomException(ErrorCode.CAMERA_NOT_FOUND);
                });

        ScenarioType scenarioType = mapToScenarioType(dto.type());

        Scenario scenario = scenarioRepository.findByScenarioType(scenarioType)
                .orElseThrow(() -> {
                    log.error("Failed to map MQTT safety event type: rawType={}, scenarioType={}",
                            dto.type(), scenarioType);
                    return new CustomException(ErrorCode.SCENARIO_NOT_FOUND);
                });

        AlertSeverity severity = mapToAlertSeverity(dto.severity());

        Instant timestampVal = dto.resolvedTimestamp() != null ? dto.resolvedTimestamp() : Instant.now();
        String messageVal = dto.message() != null ? dto.message() : (dto.type() != null ? dto.type() + " detected" : "AI safety event detected");
        Float confidenceScore = dto.confidence() != null ? dto.confidence() : 0.85f;
        String boundingBoxData = serializeBoundingBox(dto);

        AlertEvent event = AlertEvent.builder()
                .camera(camera)
                .scenario(scenario)
                .confidenceScore(confidenceScore)
                .severity(severity)
                .keypointData(messageVal)
                .boundingBoxData(boundingBoxData)
                .clipUrl(dto.clipUrl())
                .clipPath(dto.clipPath())
                .faintProb(dto.faintProb())
                .detectedAt(LocalDateTime.ofInstant(timestampVal, java.time.ZoneOffset.UTC))
                .build();

        AlertEvent saved = alertEventRepository.save(event);
        AlertEventResponse response = AlertEventResponse.from(saved);
        try {
            recentAlertCacheStore.add(camera.getFacility().getId(), response);
        } catch (RuntimeException ex) {
            log.warn("Failed to cache recent alert event: alertEventId={}, facilityId={}, error={}",
                    saved.getId(), camera.getFacility().getId(), ex.getMessage());
        }
        log.info("Saved MQTT safety alert event: alertEventId={}, cameraLoginId={}, scenarioType={}, severity={}, confidence={}, trackId={}",
                saved.getId(), finalCameraIdVal, scenarioType, severity, confidenceScore, dto.trackId());
        return response;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String serializeBoundingBox(SafetyEventDto dto) {
        if (dto.bbox() == null && dto.trackId() == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(new SafetyEventDetectionData(dto.bbox(), dto.trackId()));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize MQTT safety event bbox: cameraId={}, type={}, error={}",
                    dto.cameraId(), dto.type(), ex.getMessage());
            return null;
        }
    }

    private record SafetyEventDetectionData(List<Number> bbox, String trackId) {
    }


    private ScenarioType mapToScenarioType(String type) {
        if (type == null) return ScenarioType.SYNCOPE;
        String upper = type.toUpperCase();
        if (upper.contains("FALL")) return ScenarioType.FALL_BED;
        if (upper.contains("COLLAPSE")) return ScenarioType.COLLAPSE;
        if (upper.contains("FAINT") || upper.contains("SYNCOPE")) return ScenarioType.SYNCOPE;
        if (upper.contains("EXIT")) return ScenarioType.EXIT;
        if (upper.contains("ASSAULT") || upper.contains("VIOLENCE") || upper.contains("FIGHT")) return ScenarioType.ASSAULT;
        
        try {
            return ScenarioType.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return ScenarioType.SYNCOPE;
        }
    }

    private AlertSeverity mapToAlertSeverity(String severity) {
        if (severity == null) return AlertSeverity.CRITICAL;
        String upper = severity.toUpperCase();
        if (upper.contains("CRITICAL") || upper.contains("HIGH")) return AlertSeverity.CRITICAL;
        if (upper.contains("WARNING") || upper.contains("MEDIUM")) return AlertSeverity.WARNING;
        return AlertSeverity.WARNING;
    }
}
