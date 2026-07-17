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
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.alert.repository.AlertEventRepository;
import com.strange.safety.alert.repository.SnapshotRepository;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.event.SafetyEventDto;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.scenario.repository.ScenarioRepository;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.vlm.service.VlmDescriptionEnqueueService;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
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
import java.util.Locale;
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
    private final CorporateCameraRepository corporateCameraRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper;
    private final RecentAlertCacheStore recentAlertCacheStore;
    private final S3Service s3Service;
    private final VlmDescriptionEnqueueService vlmDescriptionEnqueueService;
    private final AlertEventDescriptionRepository alertEventDescriptionRepository;

    public Page<AlertEventResponse> getList(Long userId, Long facilityId,
                                            AlertSeverity severity, AlertStatus status,
                                            LocalDateTime dateFrom, LocalDateTime dateTo,
                                            Long cameraId, String keyword,
                                            Pageable pageable) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Specification<AlertEvent> spec;

        if (user.getRole() == Role.CORPORATE) {
            CompanyProfile profile = companyProfileRepository.findById(facilityId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
            if (!profile.getUser().getId().equals(userId)) throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
            spec = Specification.where(companyProfileEquals(facilityId));
        } else {
            facilityService.getFacilityWithOwnerCheck(userId, facilityId);
            spec = Specification.where(facilityEquals(facilityId));
        }

        spec = spec.and(severity != null ? (r, q, cb) -> cb.equal(r.get("severity"), severity) : null)
                .and(status != null ? (r, q, cb) -> cb.equal(r.get("status"), status) : null)
                .and(dateFrom != null ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("detectedAt"), dateFrom) : null)
                .and(dateTo != null ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("detectedAt"), dateTo) : null);

        if (cameraId != null) {
            if (user.getRole() == Role.CORPORATE) {
                spec = spec.and((r, q, cb) -> cb.equal(r.join("corporateCamera").get("id"), cameraId));
            } else {
                spec = spec.and((r, q, cb) -> cb.equal(r.join("camera").get("id"), cameraId));
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String likePattern = "%" + keyword.trim() + "%";
            List<ScenarioType> matchedTypes = getScenarioTypesByKeyword(keyword.trim());
            
            spec = spec.and((r, q, cb) -> {
                jakarta.persistence.criteria.Predicate keywordPredicate;
                if (user.getRole() == Role.CORPORATE) {
                    keywordPredicate = cb.or(
                            cb.like(r.get("keypointData"), likePattern),
                            cb.like(r.join("corporateCamera").get("cameraName"), likePattern)
                    );
                } else {
                    keywordPredicate = cb.or(
                            cb.like(r.get("keypointData"), likePattern),
                            cb.like(r.join("camera").get("cameraName"), likePattern)
                    );
                }

                jakarta.persistence.criteria.Subquery<Long> vlmMatch = q.subquery(Long.class);
                var descriptionRoot = vlmMatch.from(AlertEventDescription.class);
                vlmMatch.select(descriptionRoot.get("alertEvent").get("id"))
                        .where(
                                cb.equal(descriptionRoot.get("alertEvent"), r),
                                cb.equal(descriptionRoot.get("status"), VlmJobStatus.SUCCESS),
                                cb.like(descriptionRoot.get("vlmDescription"), likePattern)
                        );
                keywordPredicate = cb.or(keywordPredicate, r.get("id").in(vlmMatch));

                if (!matchedTypes.isEmpty()) {
                    jakarta.persistence.criteria.Predicate scenarioPredicate = r.join("scenario").get("scenarioType").in(matchedTypes);
                    return cb.or(keywordPredicate, scenarioPredicate);
                }
                return keywordPredicate;
            });
        }

        return alertEventRepository.findAll(spec, pageable).map(event -> {
            String snapshotUrl = event.getSnapshots().isEmpty() ? null :
                    s3Service.generatePresignedUrl(event.getSnapshots().get(0).getSnapshotUrl());
            return AlertEventResponse.from(event, snapshotUrl);
        });
    }

    public AlertEventDetailResponse getDetail(Long userId, Long alertEventId) {
        AlertEvent event = getEventWithOwnerCheck(userId, alertEventId);
        List<SnapshotResponse> snapshots = snapshotRepository.findByAlertEvent_Id(alertEventId)
                .stream()
                .map(snapshot -> {
                    String presignedUrl = s3Service.generatePresignedUrl(snapshot.getSnapshotUrl());
                    return SnapshotResponse.builder()
                            .snapshotId(snapshot.getId())
                            .snapshotUrl(presignedUrl)
                            .fileSizeBytes(snapshot.getFileSizeBytes())
                            .createdAt(snapshot.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
        String vlmDescription = alertEventDescriptionRepository.findFirstByAlertEvent_Id(alertEventId)
                .filter(row -> row.getStatus() == VlmJobStatus.SUCCESS)
                .map(AlertEventDescription::getVlmDescription)
                .orElse(null);
        return AlertEventDetailResponse.from(event, snapshots, vlmDescription);
    }

    @Transactional
    public AlertEventResponse acknowledgeByEventId(Long userId, String eventId) {
        AlertEvent event = alertEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALERT_NOT_FOUND));
        return acknowledge(userId, event.getId());
    }

    @Transactional
    public AlertEventResponse acknowledge(Long userId, Long alertEventId) {
        AlertEvent event = getEventWithOwnerCheck(userId, alertEventId);
        event.acknowledge(userRepository.getReferenceById(userId));
        String snapshotUrl = event.getSnapshots().isEmpty() ? null :
                s3Service.generatePresignedUrl(event.getSnapshots().get(0).getSnapshotUrl());
        AlertEventResponse response = AlertEventResponse.from(event, snapshotUrl);
        
        String contextKey = event.getCamera() != null
                ? "FAC_" + event.getCamera().getFacility().getId()
                : "COMP_" + event.getCorporateCamera().getCompanyProfile().getId();
        try {
            recentAlertCacheStore.update(contextKey, response);
        } catch (RuntimeException ex) {
            log.warn("Failed to update recent alert cache: alertEventId={}, contextKey={}, error={}",
                    alertEventId, contextKey, ex.getMessage());
        }

        return response;
    }

    public AlertStatsResponse getStats(Long userId, Long facilityId,
                                       LocalDateTime dateFrom, LocalDateTime dateTo) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime from = dateFrom != null ? dateFrom : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to   = dateTo   != null ? dateTo   : LocalDateTime.now().plusYears(10);

        long total, warning, critical, pending, confirmed, dismissed;
        List<AlertStatsResponse.ScenarioCount> byScenario;

        if (user.getRole() == Role.CORPORATE) {
            CompanyProfile profile = companyProfileRepository.findById(facilityId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
            if (!profile.getUser().getId().equals(userId)) throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);

            total     = alertEventRepository.countByCompanyProfileAndDateRange(facilityId, from, to);
            warning   = alertEventRepository.countByCompanyProfileAndSeverity(facilityId, AlertSeverity.WARNING, from, to);
            critical  = alertEventRepository.countByCompanyProfileAndSeverity(facilityId, AlertSeverity.CRITICAL, from, to);
            pending   = alertEventRepository.countByCompanyProfileAndStatus(facilityId, AlertStatus.PENDING, from, to);
            confirmed = alertEventRepository.countByCompanyProfileAndStatus(facilityId, AlertStatus.CONFIRMED, from, to);
            dismissed = alertEventRepository.countByCompanyProfileAndStatus(facilityId, AlertStatus.DISMISSED, from, to);
            byScenario = alertEventRepository.countGroupByScenarioForCompany(facilityId, from, to).stream()
                    .map(row -> AlertStatsResponse.ScenarioCount.builder()
                            .scenarioType((String) row[0])
                            .count(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());
        } else {
            facilityService.getFacilityWithOwnerCheck(userId, facilityId);
            total     = alertEventRepository.countByFacilityAndDateRange(facilityId, from, to);
            warning   = alertEventRepository.countByFacilityAndSeverity(facilityId, AlertSeverity.WARNING, from, to);
            critical  = alertEventRepository.countByFacilityAndSeverity(facilityId, AlertSeverity.CRITICAL, from, to);
            pending   = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.PENDING, from, to);
            confirmed = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.CONFIRMED, from, to);
            dismissed = alertEventRepository.countByFacilityAndStatus(facilityId, AlertStatus.DISMISSED, from, to);
            byScenario = alertEventRepository.countGroupByScenario(facilityId, from, to).stream()
                    .map(row -> AlertStatsResponse.ScenarioCount.builder()
                            .scenarioType((String) row[0])
                            .count(((Number) row[1]).longValue())
                            .build())
                    .collect(Collectors.toList());
        }

        return AlertStatsResponse.builder()
                .total(total).warning(warning).critical(critical)
                .pending(pending).confirmed(confirmed).dismissed(dismissed)
                .byScenario(byScenario)
                .build();
    }

    public List<AlertEventResponse> getRecent(Long userId, Long facilityId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String contextKey;

        if (user.getRole() == Role.CORPORATE) {
            CompanyProfile profile = companyProfileRepository.findById(facilityId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
            if (!profile.getUser().getId().equals(userId)) throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
            contextKey = "COMP_" + profile.getId();
        } else {
            facilityService.getFacilityWithOwnerCheck(userId, facilityId);
            contextKey = "FAC_" + facilityId;
        }

        List<AlertEventResponse> cachedEvents = recentAlertCacheStore.findRecent(contextKey);
        if (!cachedEvents.isEmpty()) {
            return cachedEvents;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<AlertEvent> events;
        if (user.getRole() == Role.CORPORATE) {
            events = alertEventRepository
                    .findTop100ByCorporateCamera_CompanyProfile_IdAndDetectedAtAfterOrderByDetectedAtDesc(facilityId, cutoff);
        } else {
            events = alertEventRepository
                    .findTop100ByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(facilityId, cutoff);
        }
        return events.stream()
                .map(event -> {
                    String snapshotUrl = event.getSnapshots().isEmpty() ? null :
                            s3Service.generatePresignedUrl(event.getSnapshots().get(0).getSnapshotUrl());
                    return AlertEventResponse.from(event, snapshotUrl);
                })
                .collect(Collectors.toList());
    }

    /**
     * AI 서버로부터 업로드된 프레임 이미지를 S3에 저장하고, 해당 AlertEvent에 Snapshot 레코드를 바인딩합니다.
     * 비동기성 레이스 컨디션(MQTT 메시지가 DB에 저장되기 전에 업로드 API가 먼저 실행되는 현상)을 방지하기 위해 1초 간격으로 최대 3회 재시도합니다.
     */
    @Transactional
    public void uploadSnapshot(String eventId, byte[] imageBytes, long size) {
        AlertEvent event = null;
        for (int i = 0; i < 3; i++) {
            event = alertEventRepository.findByEventId(eventId).orElse(null);
            if (event != null) {
                break;
            }
            try {
                log.info("[Snapshot] AlertEvent not found yet. Retrying in 1 second... eventId={}", eventId);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (event == null) {
            log.error("[Snapshot][error] Failed to find AlertEvent for snapshot upload: eventId={}", eventId);
            throw new CustomException(ErrorCode.ALERT_NOT_FOUND);
        }

        // S3에 업로드
        String objectKey = s3Service.uploadSnapshot(eventId, imageBytes);

        // Snapshot 레코드 저장
        Snapshot snapshot = Snapshot.builder()
                .alertEvent(event)
                .snapshotUrl(objectKey)
                .fileSizeBytes(size)
                .build();
        snapshotRepository.save(snapshot);
        event.getSnapshots().add(snapshot);
        log.info("[Snapshot] Saved snapshot for alertEventId={}, eventId={}, S3Key={}", event.getId(), eventId, objectKey);
        // Side-channel VLM enqueue must never break snapshot bind or primary alert path
        try {
            vlmDescriptionEnqueueService.enqueueIfMediaExists(event);
        } catch (Exception ex) {
            log.warn("[Snapshot] VLM enqueue skipped after snapshot upload eventId={}: {}", eventId, ex.getMessage());
        }
    }

    private AlertEvent getEventWithOwnerCheck(Long userId, Long alertEventId) {
        AlertEvent event = alertEventRepository.findById(alertEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALERT_NOT_FOUND));
        
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == Role.CORPORATE) {
            Long userCompanyId = companyProfileRepository.findByUser_Id(userId)
                    .map(CompanyProfile::getId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
            if (event.getCorporateCamera() == null || !event.getCorporateCamera().getCompanyProfile().getId().equals(userCompanyId)) {
                throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
            }
        } else {
            if (event.getCamera() == null) {
                throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
            }
            facilityService.getFacilityWithOwnerCheck(userId, event.getCamera().getFacility().getId());
        }
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

    private static Specification<AlertEvent> companyProfileEquals(Long companyProfileId) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(
                    root.join("corporateCamera").join("companyProfile").get("id"),
                    companyProfileId);
        };
    }

    private List<ScenarioType> getScenarioTypesByKeyword(String keyword) {
        String lower = keyword.toLowerCase();
        List<ScenarioType> matches = new java.util.ArrayList<>();
        if ("낙상".contains(lower) || "fall".contains(lower)) matches.add(ScenarioType.FALL_BED);
        if ("실신".contains(lower) || "faint".contains(lower) || "syncope".contains(lower)) matches.add(ScenarioType.SYNCOPE);
        if ("쓰러짐".contains(lower) || "collapse".contains(lower)) matches.add(ScenarioType.COLLAPSE);
        if ("폭력".contains(lower) || "싸움".contains(lower) || "assault".contains(lower)) matches.add(ScenarioType.ASSAULT);
        if ("이탈".contains(lower) || "배회".contains(lower) || "exit".contains(lower)) matches.add(ScenarioType.EXIT);
        if ("위험".contains(lower) || "침범".contains(lower) || "hazard".contains(lower)) matches.add(ScenarioType.HAZARD_ZONE);
        return matches;
    }

    public long countAllAlertsToday() {
        return alertEventRepository.countAllSince(LocalDateTime.now().minusHours(24));
    }

    @Transactional
    public AlertEventResponse createEvent(SafetyEventDto dto) {
        AlertEvent existingEvent = findExistingEvent(dto.eventId());
        if (existingEvent != null) {
            return attachClipToExisting(existingEvent, dto);
        }

        ScenarioType scenarioType = resolveScenarioType(dto.type());

        String cameraIdVal = firstNonBlank(dto.cameraLoginId(), dto.cameraId(), "cam_01");

        // Convert "cam1", "cam2" or "CCTV-01" into DB format "cam_01"
        if (cameraIdVal.startsWith("cam") && cameraIdVal.length() == 4) {
            cameraIdVal = "cam_0" + cameraIdVal.charAt(3);
        } else if (cameraIdVal.startsWith("CCTV-0") && cameraIdVal.length() == 7) {
            cameraIdVal = "cam_0" + cameraIdVal.charAt(6);
        }

        String finalCameraIdVal = cameraIdVal;
        Camera camera = cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(finalCameraIdVal, com.strange.safety.camera.entity.CameraStatus.ACTIVE).orElse(null);
        CorporateCamera corporateCamera = null;

        if (camera == null) {
            corporateCamera = corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(finalCameraIdVal, com.strange.safety.camera.entity.CameraStatus.ACTIVE)
                    .orElseThrow(() -> {
                        log.error("Failed to map MQTT safety event camera: rawCameraLoginId={}, rawCameraId={}, normalizedCameraLoginId={}",
                                dto.cameraLoginId(), dto.cameraId(), finalCameraIdVal);
                        return new CustomException(ErrorCode.CAMERA_NOT_FOUND);
                    });
        }

        Scenario scenario = scenarioRepository.findByScenarioType(scenarioType)
                .orElseThrow(() -> {
                    log.error("Failed to map MQTT safety event type: rawType={}, scenarioType={}",
                            dto.type(), scenarioType);
                    return new CustomException(ErrorCode.SCENARIO_NOT_FOUND);
                });

        AlertSeverity severity = mapToAlertSeverity(dto.severity());

        Instant timestampVal = dto.resolvedTimestamp() != null ? dto.resolvedTimestamp() : Instant.now();
        String messageVal = getDisplayMessage(scenarioType);
        Float confidenceScore = dto.confidence() != null ? dto.confidence() : 0.85f;
        String boundingBoxData = serializeBoundingBox(dto);

        AlertEvent event = AlertEvent.builder()
                .camera(camera)
                .corporateCamera(corporateCamera)
                .scenario(scenario)
                .confidenceScore(confidenceScore)
                .severity(severity)
                .keypointData(messageVal)
                .boundingBoxData(boundingBoxData)
                .clipUrl(dto.clipUrl())
                .clipObjectKey(dto.clipObjectKey())
                .clipPath(dto.clipPath())
                .faintProb(dto.faintProb())
                .detectedAt(LocalDateTime.ofInstant(timestampVal, java.time.ZoneOffset.UTC))
                .eventId(dto.eventId())
                .build();

        AlertEvent saved = alertEventRepository.save(event);
        enqueueVlmSideChannel(saved, "alert create");
        
        String s3Key = firstNonBlank(dto.clipObjectKey(), dto.clipUrl());
        if (s3Key != null && s3Key.contains(".amazonaws.com/")) {
            s3Key = s3Key.substring(s3Key.indexOf(".amazonaws.com/") + 15);
        }

        if (s3Key != null && !s3Key.trim().isEmpty()) {
            Snapshot snapshot = Snapshot.builder()
                    .alertEvent(saved)
                    .snapshotUrl(s3Key)
                    .fileSizeBytes(null)
                    .build();
            snapshotRepository.save(snapshot);
            log.info("Saved Snapshot record for alertEventId={} with snapshotUrl={}", saved.getId(), s3Key);
        }

        String snapshotUrl = (s3Key != null && !s3Key.trim().isEmpty()) ? s3Service.generatePresignedUrl(s3Key) : null;
        AlertEventResponse response = AlertEventResponse.from(saved, snapshotUrl);

        if (dto.isRealtimeEvent()) {
            String contextKey = camera != null ? "FAC_" + camera.getFacility().getId() : "COMP_" + corporateCamera.getCompanyProfile().getId();
            try {
                recentAlertCacheStore.add(contextKey, response);
            } catch (RuntimeException ex) {
                log.warn("Failed to cache recent alert event: alertEventId={}, contextKey={}, error={}",
                        saved.getId(), contextKey, ex.getMessage());
            }
        } else {
            log.info("Saved evidence safety alert event without recent-alert cache: alertEventId={}, eventId={}, clipUrl={}",
                    saved.getId(), dto.eventId(), dto.clipUrl());
        }
        log.info("Saved MQTT safety alert event: alertEventId={}, cameraLoginId={}, scenarioType={}, severity={}, confidence={}, trackId={}",
                saved.getId(), finalCameraIdVal, scenarioType, severity, confidenceScore, dto.trackId());
        return response;
    }

    private AlertEvent findExistingEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return alertEventRepository.findByEventId(eventId.trim()).orElse(null);
    }

    /**
     * 낙상 하나당 MQTT가 두 번(즉시 발행 + 클립 완성 후 재발행) 오는데, 두 번째는 이미
     * alert_events 행이 있는 재발행이므로 WebSocket/FCM 알림을 다시 보내면 안 된다.
     * AsyncEventProcessorService에서 broadcast 전에 호출해서 중복 알림을 막는다.
     */
    public boolean isAlreadyNotified(String eventId) {
        return eventId != null && !eventId.isBlank() && alertEventRepository.existsByEventId(eventId);
    }

    public boolean isSupportedEventType(String type) {
        try {
            resolveScenarioType(type);
            return true;
        } catch (CustomException ex) {
            return false;
        }
    }

    public ScenarioType resolveScenarioType(String type) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "FAINT" -> ScenarioType.COLLAPSE;
            case "FAINT_SUSPECTED", "FALL_UNRECOVERED", "SYNCOPE" -> ScenarioType.SYNCOPE;
            case "FALL", "FALL_BED" -> ScenarioType.FALL_BED;
            case "COLLAPSE" -> ScenarioType.COLLAPSE;
            case "EXIT" -> ScenarioType.EXIT;
            case "HAZARD", "HAZARD_ZONE" -> ScenarioType.HAZARD_ZONE;
            case "ASSAULT", "VIOLENCE", "FIGHT" -> ScenarioType.ASSAULT;
            default -> throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        };
    }

    public String getDisplayMessage(ScenarioType scenarioType) {
        return switch (scenarioType) {
            case COLLAPSE -> "쓰러짐 감지";
            case SYNCOPE -> "실신(미회복) 감지";
            case FALL_BED -> "낙상 감지";
            case EXIT -> "이탈 감지";
            case HAZARD_ZONE -> "위험구역 진입";
            case ASSAULT -> "폭행 감지";
            default -> "안전 이상 감지";
        };
    }

    private AlertEventResponse toResponseWithFirstSnapshot(AlertEvent event) {
        String snapshotUrl = event.getSnapshots().isEmpty() ? null :
                s3Service.generatePresignedUrl(event.getSnapshots().get(0).getSnapshotUrl());
        return AlertEventResponse.from(event, snapshotUrl);
    }

    /**
     * 낙상 즉시 발행(clip_url 없음)으로 이미 만들어진 alert_events 행에, 클립 인코딩+S3 업로드가
     * 끝난 뒤 같은 eventId로 재발행된 clip_url/clip_path를 붙인다. 새 행을 INSERT하지 않으므로
     * event_id unique 제약 위반 없이 항상 반영된다. 이번 메시지에 clip_url이 없으면(순수 중복
     * 재발행) 기존 스냅샷 기준으로 응답만 돌려주고 스킵한다.
     */
    @Transactional
    protected AlertEventResponse attachClipToExisting(AlertEvent existing, SafetyEventDto dto) {
        String clipUrl = dto.clipUrl();
        String clipObjectKey = dto.clipObjectKey();
        if ((clipUrl == null || clipUrl.isBlank())
                && (clipObjectKey == null || clipObjectKey.isBlank())) {
            log.info("Skipped duplicate MQTT safety alert event: alertEventId={}, eventId={}",
                    existing.getId(), dto.eventId());
            return toResponseWithFirstSnapshot(existing);
        }

        existing.attachClip(clipUrl, clipObjectKey, dto.clipPath());

        String s3Key = firstNonBlank(clipObjectKey, clipUrl);
        if (s3Key.contains(".amazonaws.com/")) {
            s3Key = s3Key.substring(s3Key.indexOf(".amazonaws.com/") + 15);
        }

        String snapshotUrl = null;
        if (!s3Key.trim().isEmpty()) {
            if (snapshotRepository.findByAlertEvent_Id(existing.getId()).isEmpty()) {
                Snapshot snapshot = Snapshot.builder()
                        .alertEvent(existing)
                        .snapshotUrl(s3Key)
                        .fileSizeBytes(null)
                        .build();
                snapshotRepository.save(snapshot);
                log.info("Attached snapshot to existing alertEvent: alertEventId={}, eventId={}, snapshotUrl={}",
                        existing.getId(), dto.eventId(), s3Key);
            }
            snapshotUrl = s3Service.generatePresignedUrl(s3Key);
            enqueueVlmSideChannel(existing, "clip attach");
        }

        AlertEventResponse response = AlertEventResponse.from(existing, snapshotUrl);

        // "이벤트 알림" 탭이 조회하는 최근 알림 캐시(Redis)는 1차(클립 없음) 발행 시점에만 채워지고
        // 갱신되지 않아서, 클립이 나중에 붙어도 캐시가 계속 옛 상태를 돌려주는 문제가 있었음.
        // 클립이 실제로 붙는 이 시점에 캐시에도 최신 응답을 반영한다.
        if (snapshotUrl != null) {
            String contextKey = existing.getCamera() != null
                    ? "FAC_" + existing.getCamera().getFacility().getId()
                    : "COMP_" + existing.getCorporateCamera().getCompanyProfile().getId();
            try {
                recentAlertCacheStore.add(contextKey, response);
            } catch (RuntimeException ex) {
                log.warn("Failed to refresh recent alert cache after clip attach: alertEventId={}, contextKey={}, error={}",
                        existing.getId(), contextKey, ex.getMessage());
            }
        }

        return response;
    }

    private void enqueueVlmSideChannel(AlertEvent event, String operation) {
        try {
            vlmDescriptionEnqueueService.enqueueIfMediaExists(event);
        } catch (Exception ex) {
            log.warn("VLM enqueue failed without affecting primary {} flow: eventId={}, error={}",
                    operation, event.getEventId(), ex.getMessage());
        }
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

    private AlertSeverity mapToAlertSeverity(String severity) {
        if (severity == null)
            return AlertSeverity.CRITICAL;
        String upper = severity.toUpperCase();
        if (upper.contains("CRITICAL") || upper.contains("HIGH"))
            return AlertSeverity.CRITICAL;
        if (upper.contains("WARNING") || upper.contains("MEDIUM"))
            return AlertSeverity.WARNING;
        return AlertSeverity.WARNING;
    }
}
