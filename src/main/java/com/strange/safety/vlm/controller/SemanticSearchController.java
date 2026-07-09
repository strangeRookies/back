package com.strange.safety.vlm.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.vlm.dto.SemanticSearchResultResponse;
import com.strange.safety.vlm.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SemanticSearchController {
    private final SemanticSearchService semanticSearchService;

    @GetMapping("/api/facilities/{facilityId}/search/semantic")
    public ResponseEntity<ApiResponse<List<SemanticSearchResultResponse>>> searchFacility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "0.1") double minSimilarity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(defaultValue = "true") boolean excludeMock) {
        return ResponseEntity.ok(ApiResponse.success(semanticSearchService.searchFacility(
                userDetails.getUserId(), facilityId, query, topK, minSimilarity,
                dateFrom, dateTo, cameraId, excludeMock)));
    }

    @GetMapping("/api/companies/{companyProfileId}/search/semantic")
    public ResponseEntity<ApiResponse<List<SemanticSearchResultResponse>>> searchCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long companyProfileId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "0.1") double minSimilarity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(defaultValue = "true") boolean excludeMock) {
        return ResponseEntity.ok(ApiResponse.success(semanticSearchService.searchCompany(
                userDetails.getUserId(), companyProfileId, query, topK, minSimilarity,
                dateFrom, dateTo, cameraId, excludeMock)));
    }
}
