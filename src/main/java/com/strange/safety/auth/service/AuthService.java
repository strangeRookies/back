package com.strange.safety.auth.service;

import com.strange.safety.auth.dto.LoginRequest;
import com.strange.safety.auth.dto.TokenIssueResult;
import com.strange.safety.auth.dto.TokenResponse;
import com.strange.safety.auth.security.JwtTokenProvider;
import com.strange.safety.auth.security.LoginAttemptStore;
import com.strange.safety.auth.security.RefreshTokenHasher;
import com.strange.safety.auth.session.RefreshTokenSession;
import com.strange.safety.auth.session.RefreshTokenStore;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final LoginAttemptStore loginAttemptStore;

    public TokenIssueResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> invalidCredentials(email));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getRole() != request.accountType()) {
            throw invalidCredentials(email);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.AUTH_ACCOUNT_SUSPENDED);
        }

        loginAttemptStore.clear(email);
        return issueTokens(user);
    }

    public TokenIssueResult reissue(String token) {
        String tokenHash = refreshTokenHasher.hash(token);
        RefreshTokenSession session = findRefreshToken(tokenHash);
        User user = userRepository.findByIdAndStatus(session.userId(), UserStatus.ACTIVE)
                .orElseThrow(() -> {
                    refreshTokenStore.revoke(tokenHash);
                    return new CustomException(ErrorCode.AUTH_ACCESS_DENIED);
                });

        refreshTokenStore.revoke(tokenHash);
        return issueTokens(user);
    }

    public void logout(String token) {
        String tokenHash = refreshTokenHasher.hash(token);
        findRefreshToken(tokenHash);
        refreshTokenStore.revoke(tokenHash);
    }

    private TokenIssueResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken();
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        long refreshTokenExpirationMs = jwtTokenProvider.getRefreshTokenExpirationMs();
        refreshTokenStore.save(user.getId(), refreshTokenHash, Duration.ofMillis(refreshTokenExpirationMs));

        TokenResponse response = TokenResponse.bearer(accessToken, jwtTokenProvider.getAccessTokenExpirationMs(), user);
        return new TokenIssueResult(response, refreshToken);
    }

    private RefreshTokenSession findRefreshToken(String tokenHash) {
        return refreshTokenStore.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_TOKEN));
    }

    private CustomException invalidCredentials(String email) {
        long failures = loginAttemptStore.recordFailure(email, LOGIN_LOCK_TTL);
        if (failures >= MAX_LOGIN_FAILURES) {
            return new CustomException(ErrorCode.AUTH_LOGIN_LOCKED);
        }
        return new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
