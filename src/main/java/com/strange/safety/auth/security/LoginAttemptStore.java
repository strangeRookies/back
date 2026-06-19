package com.strange.safety.auth.security;

import java.time.Duration;

public interface LoginAttemptStore {

    boolean isLocked(String email, int maxFailures);

    long recordFailure(String email, Duration lockTtl);

    void clear(String email);
}
