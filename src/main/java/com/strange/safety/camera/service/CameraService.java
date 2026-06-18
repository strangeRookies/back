package com.strange.safety.camera.service;

import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.dto.CreateCameraRequest;
import com.strange.safety.camera.dto.UpdateCameraRequest;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatusLog;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.camera.repository.CameraStatusLogRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.common.util.AesUtil;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.UserFacility;
import com.strange.safety.facility.repository.FacilityRepository;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CameraService {

    private final CameraRepository cameraRepository;
    private final CameraStatusLogRepository cameraStatusLogRepository;
    private final FacilityRepository facilityRepository;
    private final UserFacilityRepository userFacilityRepository;
    private final UserRepository userRepository;
    private final FacilityService facilityService;
    private final AesUtil aesUtil;
    private final VirtualCameraPoolService virtualCameraPoolService;
    private final RtspSimulationService rtspSimulationService;

    @Transactional
    public CameraResponse createCamera(Long userId, Long facilityId, CreateCameraRequest request) {
        if (facilityId == null) {
            Page<Facility> facilityPage = facilityRepository.findActiveFacilitiesByManagerId(
                    userId,
                    com.strange.safety.facility.entity.AccessType.MANAGER,
                    PageRequest.of(0, 1));
            if (facilityPage.isEmpty()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                if (user.getRole() == com.strange.safety.auth.entity.Role.INDIVIDUAL) {
                    Facility newFacility = facilityRepository.save(Facility.builder()
                            .facilityName(user.getName() + " 보호 시설")
                            .facilityType(com.strange.safety.facility.entity.FacilityType.HOME)
                            .postalCode("00000")
                            .address("주소 미설정")
                            .addressDetail("")
                            .district("미지정")
                            .emergency119Jurisdiction("미지정")
                            .build());
                    userFacilityRepository.save(UserFacility.builder()
                            .user(user)
                            .facility(newFacility)
                            .accessType(com.strange.safety.facility.entity.AccessType.MANAGER)
                            .build());
                    facilityId = newFacility.getId();
                } else {
                    throw new CustomException(ErrorCode.FACILITY_NOT_FOUND);
                }
            } else {
                facilityId = facilityPage.getContent().get(0).getId();
            }
        }
        Facility facility = facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        String encryptedPassword = null;
        if (request.getCameraPassword() != null) {
            encryptedPassword = aesUtil.encrypt(request.getCameraPassword());
        }

        String finalRtspUrl = request.getRtspUrl();
        String assignedVideoPath = null;

        if (request.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
            assignedVideoPath = virtualCameraPoolService.assignVideo();
            finalRtspUrl = rtspSimulationService.generateRtspUrl(request.getCameraLoginId());
        }

        Camera camera = Camera.builder()
                .facility(facility)
                .cameraLoginId(request.getCameraLoginId())
                .cameraName(request.getCameraName())
                .cameraSerialNumber(request.getCameraSerialNumber())
                .cameraPasswordEncrypted(encryptedPassword)
                .rtspUrl(finalRtspUrl)
                .locationDescription(request.getLocationDescription())
                .aiEnabled(request.getAiEnabled())
                .sourceType(request.getSourceType())
                .assignedVideoPath(assignedVideoPath)
                .build();

        camera = cameraRepository.save(camera);

        // 최초 등록 상태 로그 저장
        cameraStatusLogRepository.save(CameraStatusLog.builder()
                .camera(camera)
                .previousStatus(null)
                .currentStatus(CameraConnectionStatus.UNKNOWN)
                .reason("카메라 최초 등록")
                .detectedAt(Instant.now())
                .build());

        if (camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
            rtspSimulationService.startSimulation(camera.getCameraLoginId(), camera.getAssignedVideoPath(),
                    camera.getRtspUrl());
        }

        return CameraResponse.from(camera);
    }

    public List<CameraResponse> getCameras(Long userId, Long facilityId) {
        if (facilityId == null) {
            Page<Facility> facilityPage = facilityRepository.findActiveFacilitiesByManagerId(
                    userId,
                    com.strange.safety.facility.entity.AccessType.MANAGER,
                    PageRequest.of(0, 1));
            if (facilityPage.isEmpty()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                if (user.getRole() == com.strange.safety.auth.entity.Role.INDIVIDUAL) {
                    return java.util.Collections.emptyList();
                } else {
                    throw new CustomException(ErrorCode.FACILITY_NOT_FOUND);
                }
            }
            facilityId = facilityPage.getContent().get(0).getId();
        }
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);
        return cameraRepository.findByFacility_IdAndStatus(facilityId, CameraStatus.ACTIVE).stream()
                .map(CameraResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CameraResponse updateCamera(Long userId, Long cameraId, UpdateCameraRequest request) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, camera.getFacility().getId());

        // 이전 상태 저장
        CameraStatus oldStatus = camera.getStatus();
        boolean oldAiEnabled = camera.isAiEnabled();
        String oldLoginId = camera.getCameraLoginId();
        CameraConnectionStatus oldConnStatus = camera.getConnectionStatus();
        com.strange.safety.camera.entity.CameraSourceType oldSourceType = camera.getSourceType();

        // 카메라 정보 업데이트
        camera.update(request.getCameraName(), request.getCameraSerialNumber(), request.getRtspUrl(),
                request.getStatus(), request.getLocationDescription(), request.getAiEnabled(), request.getSourceType(),
                request.getAssignedVideoPath());

        // 새로운 상태 판별
        CameraStatus newStatus = camera.getStatus();
        boolean newAiEnabled = camera.isAiEnabled();

        boolean wasActive = (oldStatus == CameraStatus.ACTIVE && oldAiEnabled);
        boolean isActiveNow = (newStatus == CameraStatus.ACTIVE && newAiEnabled);

        if (wasActive && !isActiveNow) {
            // 1. 활성 -> 비활성 (또는 AI 사용 안함) 전환
            // 시뮬레이션 카메라였다면 FFmpeg 프로세스 중지
            if (oldSourceType == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
                rtspSimulationService.stopSimulation(oldLoginId);
            }

            // 실시간 연결 상태를 DISABLED로 업데이트
            camera.updateConnectionStatus(CameraConnectionStatus.DISABLED, Instant.now());

            // 상태 변경 로그 남기기
            cameraStatusLogRepository.save(CameraStatusLog.builder()
                    .camera(camera)
                    .previousStatus(oldConnStatus)
                    .currentStatus(CameraConnectionStatus.DISABLED)
                    .reason("관리자가 카메라를 비활성화했거나 AI 분석을 해제함")
                    .detectedAt(Instant.now())
                    .build());

        } else if (!wasActive && isActiveNow) {
            // 2. 비활성 -> 활성 (및 AI 사용) 전환
            // 실시간 연결 상태를 UNKNOWN으로 초기화하여 AI 서버의 최초 보고 대기
            camera.updateConnectionStatus(CameraConnectionStatus.UNKNOWN, Instant.now());

            // 상태 변경 로그 남기기
            cameraStatusLogRepository.save(CameraStatusLog.builder()
                    .camera(camera)
                    .previousStatus(oldConnStatus)
                    .currentStatus(CameraConnectionStatus.UNKNOWN)
                    .reason("관리자가 카메라를 활성화했거나 AI 분석을 적용함")
                    .detectedAt(Instant.now())
                    .build());

            // 시뮬레이션 카메라라면 FFmpeg 시뮬레이션 시작
            if (camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
                if (camera.getAssignedVideoPath() == null) {
                    String assignedVideoPath = virtualCameraPoolService.assignVideo();
                    camera.update(null, null, null, null, null, null, null, assignedVideoPath);
                }
                rtspSimulationService.startSimulation(camera.getCameraLoginId(), camera.getAssignedVideoPath(),
                        camera.getRtspUrl());
            }
        } else if (isActiveNow
                && camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP
                && oldSourceType == com.strange.safety.camera.entity.CameraSourceType.REAL_RTSP) {
            // 3. 활성화 상태 유지 중, 소스 타입을 REAL_RTSP -> SIMULATED_RTSP로 변경한 경우 시뮬레이션 시작
            if (camera.getAssignedVideoPath() == null) {
                String assignedVideoPath = virtualCameraPoolService.assignVideo();
                camera.update(null, null, null, null, null, null, null, assignedVideoPath);
            }
            rtspSimulationService.startSimulation(camera.getCameraLoginId(), camera.getAssignedVideoPath(),
                    camera.getRtspUrl());
        } else if (wasActive && oldSourceType == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP
                && camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.REAL_RTSP) {
            // 4. 활성화 상태 유지 중, 소스 타입을 SIMULATED_RTSP -> REAL_RTSP로 변경한 경우 시뮬레이션 중지
            rtspSimulationService.stopSimulation(oldLoginId);
        }

        return CameraResponse.from(camera);
    }

    @Transactional
    public void deleteCamera(Long userId, Long cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, camera.getFacility().getId());

        if (camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
            rtspSimulationService.stopSimulation(camera.getCameraLoginId());
        }

        cameraRepository.delete(camera);
    }

    public List<CameraResponse> getActiveAiCameras() {
        return cameraRepository.findByAiEnabledTrueAndStatus(CameraStatus.ACTIVE).stream()
                .map(CameraResponse::from)
                .collect(Collectors.toList());
    }

    public List<CameraResponse> getCamerasForAdmin(Long facilityId) {
        facilityService.getFacilityForAdmin(facilityId);
        return cameraRepository.findByFacility_Id(facilityId).stream()
                .map(CameraResponse::from)
                .collect(Collectors.toList());
    }
}
