package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.Facility;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminFacilityResponse {

    private Long facilityId;
    private String facilityName;

    public static AdminFacilityResponse from(Facility facility) {
        return AdminFacilityResponse.builder()
                .facilityId(facility.getId())
                .facilityName(facility.getFacilityName())
                .build();
    }
}
