package com.strange.safety.auth.service;

import com.strange.safety.auth.dto.LoginRequest;
import com.strange.safety.auth.dto.TokenIssueResult;
import com.strange.safety.auth.dto.TokenResponse;
import com.strange.safety.auth.entity.RefreshToken;
import com.strange.safety.auth.repository.RefreshTokenRepository;
import com.strange.safety.auth.security.JwtTokenProvider;
import com.strange.safety.auth.security.RefreshTokenHasher;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;

    public TokenIssueResult login(LoginRequest request) {
        User user = userRepository.findByEmailAndStatus(normalizeEmail(request.email()), UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getRole() != request.accountType()) {
            throw new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    public TokenIssueResult reissue(String token) {
        RefreshToken refreshToken = findRefreshToken(token);
        Instant now = Instant.now();
        if (refreshToken.isRevoked() || refreshToken.isExpired(now)) {
            refreshToken.revoke();
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        if (refreshToken.getUser().getStatus() != UserStatus.ACTIVE) {
            refreshToken.revoke();
            throw new CustomException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        refreshToken.revoke();
        return issueTokens(refreshToken.getUser());
    }

    public void logout(String token) {
        RefreshToken refreshToken = findRefreshToken(token);
        refreshToken.revoke();
    }

    private TokenIssueResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken();
        Instant refreshTokenExpiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs());
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                refreshTokenHasher.hash(refreshToken),
                refreshTokenExpiresAt
        ));

        TokenResponse response = TokenResponse.bearer(accessToken, jwtTokenProvider.getAccessTokenExpirationMs(), user);
        return new TokenIssueResult(response, refreshToken);
    }

    private RefreshToken findRefreshToken(String token) {
        return refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(token))
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_TOKEN));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
