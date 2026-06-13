package com.strange.safety.company.controller;

import com.strange.safety.auth.dto.AvailabilityResponse;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.company.dto.BusinessNumberAvailabilityRequest;
import com.strange.safety.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final SignupService signupService;

    @PostMapping("/business-number-availability")
    public ApiResponse<AvailabilityResponse> businessNumberAvailability(
            @Valid @RequestBody BusinessNumberAvailabilityRequest request) {
        return ApiResponse.success(signupService.businessNumberAvailability(request.businessNumber()));
    }
}
