package com.strange.safety.auth.session;

public record RefreshTokenSession(
        Long userId,
        String tokenHash
) {
}
