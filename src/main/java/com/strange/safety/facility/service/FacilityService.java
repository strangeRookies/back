package com.strange.safety.facility.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.facility.dto.AdminFacilityResponse;
import com.strange.safety.facility.dto.CreateFacilityRequest;
import com.strange.safety.facility.dto.FacilityResponse;
import com.strange.safety.facility.dto.UpdateFacilityRequest;
import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.UserFacility;
import com.strange.safety.facility.repository.FacilityRepository;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final UserFacilityRepository userFacilityRepository;
    private final UserRepository userRepository;

    @Transactional
    public FacilityResponse createFacility(Long userId, CreateFacilityRequest request) {
        Facility facility = Facility.builder()
                .facilityName(request.getFacilityName())
                .facilityType(request.getFacilityType())
                .postalCode(request.getPostalCode())
                .address(request.getAddress())
                .addressDetail(request.getAddressDetail())
                .emergency119Jurisdiction(request.getEmergency119Jurisdiction())
                .build();
        facilityRepository.save(facility);

        UserFacility userFacility = UserFacility.builder()
                .user(userRepository.getReferenceById(userId))
                .facility(facility)
                .accessType(AccessType.MANAGER)
                .build();
        userFacilityRepository.save(userFacility);

        return FacilityResponse.from(facility);
    }

    public Page<FacilityResponse> getFacilities(Long userId, Pageable pageable) {
        return facilityRepository
                .findActiveFacilitiesByManagerId(userId, AccessType.MANAGER, pageable)
                .map(FacilityResponse::from);
    }

    @Transactional
    public FacilityResponse updateFacility(Long userId, Long facilityId, UpdateFacilityRequest request) {
        Facility facility = getFacilityWithOwnerCheck(userId, facilityId);
        facility.update(request.getFacilityName(), request.getAddress(),
                request.getAddressDetail(), request.getPostalCode(),
                request.getEmergency119Jurisdiction());
        return FacilityResponse.from(facility);
    }

    @Transactional
    public void deleteFacility(Long userId, Long facilityId) {
        Facility facility = getFacilityWithOwnerCheck(userId, facilityId);
        facility.deactivate();
    }

    public Facility getFacilityWithOwnerCheck(Long userId, Long facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .filter(Facility::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.FACILITY_NOT_FOUND));
        if (!userFacilityRepository.existsByUser_IdAndFacility_IdAndAccessType(
                userId, facilityId, AccessType.MANAGER)) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
        return facility;
    }

    public Facility getFacilityForAdmin(Long facilityId) {
        return facilityRepository.findById(facilityId)
                .filter(Facility::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.FACILITY_NOT_FOUND));
    }

    public List<AdminFacilityResponse> getAllIndividualFacilitiesForAdmin() {
        return facilityRepository.findAllActiveIndividualFacilities().stream()
                .map(AdminFacilityResponse::from)
                .collect(Collectors.toList());
    }
}
