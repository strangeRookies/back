package com.strange.safety.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.auth.dto.PasswordResetRequest;
import com.strange.safety.auth.dto.PasswordResetSmsConfirmRequest;
import com.strange.safety.auth.dto.PasswordResetSmsRequest;
import com.strange.safety.auth.dto.PasswordResetSmsResponse;
import com.strange.safety.auth.dto.SmsVerificationRequest;
import com.strange.safety.auth.dto.SmsVerificationConfirmResponse;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.auth.session.RefreshTokenStore;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private SmsVerificationService smsVerificationService;

    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new PasswordResetService(userRepository, refreshTokenStore, passwordEncoder, smsVerificationService);
    }

    @Test
    void sendSmsIssuesResetPasswordVerificationWhenEmailAndPhoneMatch() {
        User user = user("user@example.com", "01012345678", "old-password-hash");
        when(smsVerificationService.normalizePhone("010-1234-5678")).thenReturn("01012345678");
        when(userRepository.findByEmailAndPhoneNumberAndStatus(
                "user@example.com", "01012345678", UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(smsVerificationService.send(any(SmsVerificationRequest.class)))
                .thenReturn(new com.strange.safety.auth.dto.SmsVerificationResponse(1L, 300));

        PasswordResetSmsResponse response = service.sendSms(
                new PasswordResetSmsRequest("USER@example.com", "010-1234-5678"));

        ArgumentCaptor<SmsVerificationRequest> captor = ArgumentCaptor.forClass(SmsVerificationRequest.class);
        verify(smsVerificationService).send(captor.capture());
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(captor.getValue().phone()).isEqualTo("01012345678");
        assertThat(captor.getValue().purpose()).isEqualTo(VerificationPurpose.RESET_PASSWORD);
    }

    @Test
    void sendSmsReturnsSameAcceptedResponseWhenUserDoesNotMatchEmailAndPhone() {
        when(smsVerificationService.normalizePhone("01012345678")).thenReturn("01012345678");
        when(userRepository.findByEmailAndPhoneNumberAndStatus(
                "missing@example.com", "01012345678", UserStatus.ACTIVE)).thenReturn(Optional.empty());

        PasswordResetSmsResponse response = service.sendSms(
                new PasswordResetSmsRequest("missing@example.com", "01012345678"));

        assertThat(response.expiresIn()).isEqualTo(300);
        verify(smsVerificationService, never()).send(any(SmsVerificationRequest.class));
    }

    @Test
    void confirmSmsIssuesVerificationTokenWhenEmailAndPhoneMatch() {
        User user = user("user@example.com", "01012345678", "old-password-hash");
        when(smsVerificationService.normalizePhone("01012345678")).thenReturn("01012345678");
        when(userRepository.findByEmailAndPhoneNumberAndStatus(
                "user@example.com", "01012345678", UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(smsVerificationService.confirmLatest("01012345678", VerificationPurpose.RESET_PASSWORD, "123456"))
                .thenReturn(new SmsVerificationConfirmResponse(true, "verified-token"));

        SmsVerificationConfirmResponse response = service.confirmSms(
                new PasswordResetSmsConfirmRequest("user@example.com", "01012345678", "123456"));

        assertThat(response.verified()).isTrue();
        assertThat(response.verificationToken()).isEqualTo("verified-token");
    }

    @Test
    void confirmSmsFailsWithGenericVerificationErrorWhenUserDoesNotMatchEmailAndPhone() {
        when(smsVerificationService.normalizePhone("01012345678")).thenReturn("01012345678");
        when(userRepository.findByEmailAndPhoneNumberAndStatus(
                "missing@example.com", "01012345678", UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmSms(
                new PasswordResetSmsConfirmRequest("missing@example.com", "01012345678", "123456")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION);
        verify(smsVerificationService, never()).confirmLatest(any(), any(), any());
    }

    @Test
    void resetPasswordChangesPasswordAndRevokesRefreshTokens() {
        User user = user("user@example.com", "01012345678", passwordEncoder.encode("OldPassword123!"));
        when(smsVerificationService.normalizePhone("01012345678")).thenReturn("01012345678");
        when(userRepository.findByEmailAndPhoneNumberAndStatus(
                "user@example.com", "01012345678", UserStatus.ACTIVE)).thenReturn(Optional.of(user));

        service.resetPassword(new PasswordResetRequest(
                "user@example.com", "01012345678", "verified-token", "NewPassword123!"));

        verify(smsVerificationService).consume(
                "verified-token", "01012345678", VerificationPurpose.RESET_PASSWORD);
        assertThat(passwordEncoder.matches("NewPassword123!", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123!", user.getPasswordHash())).isFalse();
        verify(refreshTokenStore).revokeAllByUserId(1L);
    }

    private User user(String email, String phone, String passwordHash) {
        User user = User.create(email, passwordHash, "사용자", phone, Role.INDIVIDUAL);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
