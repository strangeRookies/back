package com.strange.safety.auth.dto;

public record TokenIssueResult(
        TokenResponse response,
        String refreshToken
) {
}
