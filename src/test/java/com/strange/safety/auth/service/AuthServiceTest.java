package com.strange.safety.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.auth.dto.LoginRequest;
import com.strange.safety.auth.dto.TokenIssueResult;
import com.strange.safety.auth.entity.Role;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "password123";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String REFRESH_TOKEN_HASH = "refresh-token-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private LoginAttemptStore loginAttemptStore;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository,
                refreshTokenStore,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenHasher,
                loginAttemptStore
        );
    }

    @Test
    void loginIssuesTokens() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        when(userRepository.findByEmailAndStatus("test@example.com", UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(1800000L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(1209600000L);
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);

        TokenIssueResult result = authService.login(
                new LoginRequest("test@example.com", RAW_PASSWORD, Role.INDIVIDUAL));

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        verify(refreshTokenStore).save(any(Long.class), any(String.class), any(Duration.class));
        verify(loginAttemptStore).clear("test@example.com");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        when(userRepository.findByEmailAndStatus("test@example.com", UserStatus.ACTIVE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", "wrong-password", Role.INDIVIDUAL)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(loginAttemptStore).recordFailure(any(String.class), any(Duration.class));
    }

    @Test
    void loginFailsWhenAccountTypeDoesNotMatchStoredRole() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        when(userRepository.findByEmailAndStatus("test@example.com", UserStatus.ACTIVE)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", RAW_PASSWORD, Role.CORPORATE)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(loginAttemptStore).recordFailure(any(String.class), any(Duration.class));
    }

    @Test
    void loginFailsWhenUserIsNotActive() {
        when(userRepository.findByEmailAndStatus("test@example.com", UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", RAW_PASSWORD, Role.INDIVIDUAL)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(loginAttemptStore).recordFailure(any(String.class), any(Duration.class));
    }

    @Test
    void loginFailsWhenEmailIsLocked() {
        when(loginAttemptStore.isLocked("test@example.com", 5)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", RAW_PASSWORD, Role.INDIVIDUAL)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
        verify(userRepository, never()).findByEmailAndStatus(any(String.class), any(UserStatus.class));
    }

    @Test
    void loginLocksEmailOnFifthFailure() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        when(userRepository.findByEmailAndStatus("test@example.com", UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(loginAttemptStore.recordFailure(any(String.class), any(Duration.class))).thenReturn(5L);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", "wrong-password", Role.INDIVIDUAL)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
    }

    @Test
    void reissueRotatesRefreshToken() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(new RefreshTokenSession(1L, REFRESH_TOKEN_HASH)));
        when(userRepository.findByIdAndStatus(1L, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken()).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(1800000L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(1209600000L);
        when(refreshTokenHasher.hash("new-refresh-token")).thenReturn("new-refresh-token-hash");

        TokenIssueResult result = authService.reissue(REFRESH_TOKEN);

        verify(refreshTokenStore).revoke(REFRESH_TOKEN_HASH);
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void reissueFailsWhenUserIsNotActive() {
        User user = user("test@example.com", passwordEncoder.encode(RAW_PASSWORD));
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(new RefreshTokenSession(1L, REFRESH_TOKEN_HASH)));
        when(userRepository.findByIdAndStatus(1L, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(REFRESH_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_ACCESS_DENIED);
        verify(refreshTokenStore).revoke(REFRESH_TOKEN_HASH);
    }

    @Test
    void logoutRevokesRefreshTokenAndPreventsReuse() {
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(new RefreshTokenSession(1L, REFRESH_TOKEN_HASH)));

        authService.logout(REFRESH_TOKEN);

        verify(refreshTokenStore).revoke(REFRESH_TOKEN_HASH);
        when(refreshTokenStore.findByTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.reissue(REFRESH_TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
    }

    private User user(String email, String passwordHash) {
        User user = User.create(email, passwordHash, "홍길동", null, Role.INDIVIDUAL);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
