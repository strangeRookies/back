package com.strange.safety.company.controller;

import com.strange.safety.auth.dto.AvailabilityResponse;
import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.company.dto.BusinessNumberAvailabilityRequest;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.company.dto.AdminCompanyResponse;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final SignupService signupService;
    private final CompanyProfileRepository companyProfileRepository;

    @PostMapping("/business-number-availability")
    public ApiResponse<AvailabilityResponse> businessNumberAvailability(
            @Valid @RequestBody BusinessNumberAvailabilityRequest request) {
        return ApiResponse.success(signupService.businessNumberAvailability(request.businessNumber()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminCompanyResponse> getMyCompanyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        CompanyProfile profile = companyProfileRepository.findByUser_Id(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Company profile not found for user: " + userDetails.getUserId()));
        return ApiResponse.success(AdminCompanyResponse.from(profile));
    }
}
