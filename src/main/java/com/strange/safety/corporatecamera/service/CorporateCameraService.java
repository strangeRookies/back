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

    @Transactional
    public CorporateCameraResponse register(Long companyProfileId, CorporateCameraRequest request) {
        CompanyProfile companyProfile = companyProfileRepository.findById(companyProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));

        String encryptedPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? aesUtil.encrypt(request.getPassword()) : null;

        CorporateCamera camera = CorporateCamera.builder()
                .companyProfile(companyProfile)
                .cameraName(request.getCameraName())
                .cameraSerialNumber(request.getCameraSerialNumber())
                .cameraLoginId(request.getCameraLoginId())
                .cameraPasswordEncrypted(encryptedPassword)
                .rtspUrl(request.getRtspUrl())
                .locationDescription(request.getLocationDescription())
                .sourceType(request.getSourceType())
                .assignedVideoPath(request.getAssignedVideoPath())
                .build();

        return CorporateCameraResponse.from(corporateCameraRepository.save(camera));
    }

    public List<CorporateCameraResponse> getCamerasByCompany(Long companyProfileId) {
        if (!companyProfileRepository.existsById(companyProfileId)) {
            throw new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND);
        }
        return corporateCameraRepository.findByCompanyProfile_Id(companyProfileId)
                .stream().map(CorporateCameraResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public void deleteCamera(Long cameraId) {
        CorporateCamera camera = corporateCameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        corporateCameraRepository.delete(camera);
    }
}
