package com.strange.safety.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.strange.safety.user.dto.AgreementRequest;

public record CorporateSignupRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 100) @NotBlank String password,
        @NotBlank String phone,
        @NotBlank String verificationToken,
        @Valid @NotNull CompanyRequest company,
        @Valid @NotNull ManagerRequest manager,
        AgreementRequest agreements
) {
    public record CompanyRequest(
            @NotBlank String name,
            String businessNumber,
            @NotBlank String industry,
            @NotBlank String size,
            @NotBlank String postcode,
            @NotBlank String address,
            String addressDetail,
            String region3DepthName,
            String district,
            String jurisdiction
    ) {
    }

    public record ManagerRequest(
            @NotBlank String name,
            String department,
            @Email @NotBlank String email,
            @NotBlank String contact
    ) {
    }
}
