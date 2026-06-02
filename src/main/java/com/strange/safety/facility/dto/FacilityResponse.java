package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.entity.FacilityType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FacilityResponse {

    private Long facilityId;
    private String facilityName;
    private FacilityType facilityType;
    private String postalCode;
    private String address;
    private String addressDetail;
    private String emergency119Jurisdiction;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FacilityResponse from(Facility facility) {
        return FacilityResponse.builder()
                .facilityId(facility.getId())
                .facilityName(facility.getFacilityName())
                .facilityType(facility.getFacilityType())
                .postalCode(facility.getPostalCode())
                .address(facility.getAddress())
                .addressDetail(facility.getAddressDetail())
                .emergency119Jurisdiction(facility.getEmergency119Jurisdiction())
                .isActive(facility.isActive())
                .createdAt(facility.getCreatedAt())
                .updatedAt(facility.getUpdatedAt())
                .build();
    }
}
