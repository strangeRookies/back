package com.strange.safety.facility.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.facility.dto.CreateProtectedTargetRequest;
import com.strange.safety.facility.dto.ProtectedTargetResponse;
import com.strange.safety.facility.dto.UpdateProtectedTargetRequest;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.ProtectedTarget;
import com.strange.safety.facility.repository.ProtectedTargetRepository;
import com.strange.safety.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProtectedTargetService {

    private final ProtectedTargetRepository protectedTargetRepository;
    private final FacilityService facilityService;
    private final UserRepository userRepository;

    @Transactional
    public ProtectedTargetResponse createProtectedTarget(Long userId, Long facilityId,
                                                         CreateProtectedTargetRequest request) {
        Facility facility = facilityService.getFacilityWithOwnerCheck(userId, facilityId);

        ProtectedTarget target = ProtectedTarget.builder()
                .guardianUser(userRepository.getReferenceById(userId))
                .facility(facility)
                .targetName(request.getTargetName())
                .relationship(request.getRelationship())
                .ageGroup(request.getAgeGroup())
                .build();

        return ProtectedTargetResponse.from(protectedTargetRepository.save(target));
    }

    public Page<ProtectedTargetResponse> getProtectedTargets(Long userId, Long facilityId,
                                                              Pageable pageable) {
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);
        return protectedTargetRepository.findByFacility_Id(facilityId, pageable)
                .map(ProtectedTargetResponse::from);
    }

    public ProtectedTargetResponse getProtectedTarget(Long userId, Long targetId) {
        ProtectedTarget target = protectedTargetRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROTECTED_TARGET_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, target.getFacility().getId());
        return ProtectedTargetResponse.from(target);
    }

    @Transactional
    public ProtectedTargetResponse updateProtectedTarget(Long userId, Long targetId,
                                                          UpdateProtectedTargetRequest request) {
        ProtectedTarget target = protectedTargetRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROTECTED_TARGET_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, target.getFacility().getId());
        target.update(request.getTargetName(), request.getRelationship(), request.getAgeGroup());
        return ProtectedTargetResponse.from(target);
    }

    @Transactional
    public void deleteProtectedTarget(Long userId, Long targetId) {
        ProtectedTarget target = protectedTargetRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROTECTED_TARGET_NOT_FOUND));
        facilityService.getFacilityWithOwnerCheck(userId, target.getFacility().getId());
        protectedTargetRepository.delete(target);
    }
}
