package com.strange.safety.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.strange.safety.user.dto.AgreementRequest;

public record IndividualSignupRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 100) @NotBlank String password,
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String verificationToken,
        @Valid @NotNull CareTargetRequest careTarget,
        @Valid List<EmergencyContactRequest> emergencyContacts,
        AgreementRequest agreements
) {
    public record CareTargetRequest(
            @NotBlank String name,
            @NotBlank String relation,
            @NotBlank String ageGroup,
            @NotBlank String postcode,
            @NotBlank String address,
            String addressDetail,
            String region3DepthName,
            String district,
            String jurisdiction
    ) {
    }

    public record EmergencyContactRequest(
            @NotBlank String name,
            @NotBlank String relation,
            @NotBlank String phone
    ) {
    }
}
