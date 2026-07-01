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
    private final com.strange.safety.corporatecamera.repository.CorporateCameraRepository corporateCameraRepository;

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

        if (cameraRepository.existsByCameraLoginId(request.getCameraLoginId()) ||
            corporateCameraRepository.existsByCameraLoginId(request.getCameraLoginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_CAMERA_LOGIN_ID);
        }

        String encryptedPassword = null;
        if (request.getCameraPassword() != null) {
            encryptedPassword = aesUtil.encrypt(request.getCameraPassword());
        }

        String finalRtspUrl = request.getRtspUrl();
        String assignedVideoPath = null;
        assignedVideoPath = virtualCameraPoolService.assignVideo();
        finalRtspUrl = rtspSimulationService.generateRtspUrl(request.getCameraLoginId());

        Camera camera = Camera.builder()
                .facility(facility)
                .cameraLoginId(request.getCameraLoginId())
                .cameraName(request.getCameraName())
                .cameraSerialNumber(request.getCameraSerialNumber())
                .cameraPasswordEncrypted(encryptedPassword)
                .rtspUrl(finalRtspUrl)
                .locationDescription(request.getLocationDescription())
                .aiEnabled(request.getAiEnabled())
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

        rtspSimulationService.startSimulation(camera.getCameraLoginId(), camera.getAssignedVideoPath(),
                camera.getRtspUrl());

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

        camera.update(request.getCameraName(), request.getCameraSerialNumber(), request.getRtspUrl(),
                request.getStatus(), request.getLocationDescription(), request.getAiEnabled(),
                request.getAssignedVideoPath());

        return CameraResponse.from(camera);
    }

    @Transactional
    public void deleteCamera(Long userId, Long cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, camera.getFacility().getId());
        if (camera.getAssignedVideoPath() != null) {
            rtspSimulationService.stopSimulation(camera.getCameraLoginId());
        }

        cameraRepository.delete(camera);
    }

    public List<CameraResponse> getActiveAiCameras() {
        List<CameraResponse> result = new java.util.ArrayList<>();

        result.addAll(cameraRepository.findAiEnabledWithRoisAndScenarios(CameraStatus.ACTIVE).stream()
                .map(camera -> {
                    List<com.strange.safety.camera.entity.RoiConfig> activeRois = camera.getRoiConfigs().stream()
                            .filter(com.strange.safety.camera.entity.RoiConfig::isActive)
                            .toList();
                    return CameraResponse.fromWithRoi(camera, activeRois);
                })
                .collect(Collectors.toList()));

        result.addAll(corporateCameraRepository.findByStatus(CameraStatus.ACTIVE).stream()
                .map(CameraResponse::fromCorporate)
                .collect(Collectors.toList()));

        return result;
    }

    public List<CameraResponse> getCamerasForAdmin(Long facilityId) {
        facilityService.getFacilityForAdmin(facilityId);
        return cameraRepository.findByFacility_Id(facilityId).stream()
                .map(CameraResponse::from)
                .collect(Collectors.toList());
    }
}
