package com.strange.safety.company.controller;

import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.company.dto.AdminCompanyResponse;
import com.strange.safety.company.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final CompanyProfileRepository companyProfileRepository;

    @GetMapping("/company-profiles")
    public ResponseEntity<ApiResponse<List<AdminCompanyResponse>>> getAllCompanyProfiles() {
        List<AdminCompanyResponse> profiles = companyProfileRepository.findAll()
                .stream().map(AdminCompanyResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(profiles));
    }
}
