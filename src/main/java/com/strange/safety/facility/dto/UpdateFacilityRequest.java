package com.strange.safety.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateFacilityRequest {

    private String facilityName;
    private String address;
    private String addressDetail;
    private String postalCode;
    private String emergency119Jurisdiction;
}
