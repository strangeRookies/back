package com.strange.safety.company.dto;

import jakarta.validation.constraints.NotBlank;

public record BusinessNumberAvailabilityRequest(
        @NotBlank String businessNumber
) {
}
