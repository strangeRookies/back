package com.strange.safety.facility.dto;

import jakarta.validation.constraints.NotBlank;

public record EmergencyJurisdictionResolveRequest(
        String postcode,
        @NotBlank String address,
        String addressDetail,
        String region3DepthName
) {
}
