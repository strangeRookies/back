package com.strange.safety.auth.session;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(Long userId, String tokenHash, Duration ttl);

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    void revoke(String tokenHash);

    void revokeAllByUserId(Long userId);
}
