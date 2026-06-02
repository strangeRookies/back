package com.strange.safety.camera.service;

import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.dto.CreateCameraRequest;
import com.strange.safety.camera.dto.UpdateCameraRequest;
import com.strange.safety.camera.entity.Camera;
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

    @Transactional
    public CameraResponse createCamera(Long userId, Long facilityId, CreateCameraRequest request) {
        Facility facility = facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        String encryptedPassword = null;
        if (request.getCameraPassword() != null) {
            encryptedPassword = aesUtil.encrypt(request.getCameraPassword());
        }

        Camera camera = Camera.builder()
                .facility(facility)
                .cameraLoginId(request.getCameraLoginId())
                .cameraPasswordEncrypted(encryptedPassword)
                .rtspUrl(request.getRtspUrl())
                .locationDescription(request.getLocationDescription())
                .build();

        return CameraResponse.from(cameraRepository.save(camera));
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
        camera.update(request.getRtspUrl(), request.getStatus(), request.getLocationDescription());
        return CameraResponse.from(camera);
    }

    @Transactional
    public void deleteCamera(Long userId, Long cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, camera.getFacility().getId());
        camera.deactivate();
    }
}
