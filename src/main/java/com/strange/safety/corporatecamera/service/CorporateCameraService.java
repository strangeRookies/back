package com.strange.safety.corporatecamera.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.common.util.AesUtil;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.corporatecamera.dto.CorporateCameraRequest;
import com.strange.safety.corporatecamera.dto.CorporateCameraResponse;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.camera.repository.CameraStatusLogRepository;
import com.strange.safety.camera.entity.CameraStatusLog;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.service.RtspSimulationService;
import com.strange.safety.camera.service.VirtualCameraPoolService;
import com.strange.safety.camera.repository.CameraRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorporateCameraService {

    private final CorporateCameraRepository corporateCameraRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final AesUtil aesUtil;
    private final RtspSimulationService rtspSimulationService;
    private final VirtualCameraPoolService virtualCameraPoolService;
    private final CameraRepository cameraRepository;
    private final CameraStatusLogRepository cameraStatusLogRepository;

    @Transactional
    public CorporateCameraResponse register(Long companyProfileId, CorporateCameraRequest request) {
        CompanyProfile companyProfile = companyProfileRepository.findById(companyProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));

        if (corporateCameraRepository.existsByCameraLoginId(request.getCameraLoginId()) ||
            cameraRepository.existsByCameraLoginId(request.getCameraLoginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_CAMERA_LOGIN_ID);
        }

        String encryptedPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? aesUtil.encrypt(request.getPassword()) : null;

        // Force simulation logic for corporate cameras
        String assignedVideoPath = virtualCameraPoolService.assignVideo();
        String finalRtspUrl = rtspSimulationService.generateRtspUrl(request.getCameraLoginId());

        CorporateCamera camera = CorporateCamera.builder()
                .companyProfile(companyProfile)
                .cameraName(request.getCameraName())
                .cameraSerialNumber(request.getCameraSerialNumber())
                .cameraLoginId(request.getCameraLoginId())
                .cameraPasswordEncrypted(encryptedPassword)
                .rtspUrl(finalRtspUrl)
                .locationDescription(request.getLocationDescription())
                .assignedVideoPath(assignedVideoPath)
                .build();

        camera = corporateCameraRepository.save(camera);
        
        // 최초 등록 상태 로그 저장
        cameraStatusLogRepository.save(CameraStatusLog.builder()
                .corporateCamera(camera)
                .previousStatus(null)
                .currentStatus(CameraConnectionStatus.UNKNOWN)
                .reason("카메라 최초 등록")
                .detectedAt(Instant.now())
                .build());

        final String loginId = camera.getCameraLoginId();
        final String videoPath = camera.getAssignedVideoPath();
        final String rtspUrl = camera.getRtspUrl();
        
        rtspSimulationService.startSimulation(loginId, videoPath, rtspUrl);

        return CorporateCameraResponse.from(camera);
    }

    public List<CorporateCameraResponse> getCamerasByCompany(Long companyProfileId) {
        if (!companyProfileRepository.existsById(companyProfileId)) {
            throw new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND);
        }
        return corporateCameraRepository.findByCompanyProfile_Id(companyProfileId)
                .stream().map(CorporateCameraResponse::from).collect(Collectors.toList());
    }

    public List<CorporateCameraResponse> getMyCameras(Long userId) {
        CompanyProfile companyProfile = companyProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
        return getCamerasByCompany(companyProfile.getId());
    }

    @Transactional
    public void deleteCamera(Long cameraId) {
        CorporateCamera camera = corporateCameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        corporateCameraRepository.delete(camera);
    }
}
