package com.strange.safety.camera.service;

import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.dto.CreateCameraRequest;
import com.strange.safety.camera.dto.UpdateCameraRequest;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.common.util.AesUtil;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CameraService {

    private final CameraRepository cameraRepository;
    private final FacilityService facilityService;
    private final AesUtil aesUtil;
    private final VirtualCameraPoolService virtualCameraPoolService;
    private final RtspSimulationService rtspSimulationService;

    @Transactional
    public CameraResponse createCamera(Long userId, Long facilityId, CreateCameraRequest request) {
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

        if (camera.getSourceType() == com.strange.safety.camera.entity.CameraSourceType.SIMULATED_RTSP) {
            rtspSimulationService.startSimulation(camera.getCameraLoginId(), camera.getAssignedVideoPath(), camera.getRtspUrl());
        }

        return CameraResponse.from(camera);
    }

    public List<CameraResponse> getCameras(Long userId, Long facilityId) {
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);
        return cameraRepository.findByFacility_Id(facilityId).stream()
                .map(CameraResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CameraResponse updateCamera(Long userId, Long cameraId, UpdateCameraRequest request) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, camera.getFacility().getId());
        camera.update(request.getCameraName(), request.getCameraSerialNumber(), request.getRtspUrl(), request.getStatus(), request.getLocationDescription(), request.getAiEnabled(), request.getSourceType(), request.getAssignedVideoPath());

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
        
        camera.deactivate();
    }

    public List<CameraResponse> getActiveAiCameras() {
        return cameraRepository.findAll().stream()
                .filter(Camera::isAiEnabled)
                .filter(c -> c.getStatus() == CameraStatus.ACTIVE)
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
