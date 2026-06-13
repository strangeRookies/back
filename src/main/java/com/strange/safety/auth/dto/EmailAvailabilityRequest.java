package com.strange.safety.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailAvailabilityRequest(
        @Email @NotBlank String email
) {
}
