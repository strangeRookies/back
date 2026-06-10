package com.strange.safety.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.strange.safety.auth.dto.SmsVerificationConfirmRequest;
import com.strange.safety.auth.dto.SmsVerificationRequest;
import com.strange.safety.auth.dto.SmsVerificationResponse;
import com.strange.safety.auth.entity.SmsVerification;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.auth.repository.SmsVerificationRepository;
import com.strange.safety.auth.security.RefreshTokenHasher;
import com.strange.safety.auth.sms.SmsCodeGenerator;
import com.strange.safety.auth.sms.SmsProperties;
import com.strange.safety.auth.sms.SmsSendException;
import com.strange.safety.auth.sms.SmsSender;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SmsVerificationServiceTest {

    @Mock
    private SmsVerificationRepository repository;
    @Mock
    private RefreshTokenHasher tokenHasher;
    @Mock
    private SmsSender smsSender;

    private SmsVerificationService service;
    private SmsProperties smsProperties;

    @BeforeEach
    void setUp() {
        smsProperties = new SmsProperties();
        SmsCodeGenerator codeGenerator = () -> "123456";
        service = new SmsVerificationService(
                repository, new BCryptPasswordEncoder(), tokenHasher, smsSender, codeGenerator, smsProperties);
    }

    @Test
    void sendAndConfirmIssuesSingleUseVerificationToken() {
        when(repository.save(any(SmsVerification.class))).thenAnswer(invocation -> {
            SmsVerification verification = invocation.getArgument(0);
            ReflectionTestUtils.setField(verification, "id", 1L);
            return verification;
        });
        SmsVerificationResponse issued = service.send(
                new SmsVerificationRequest("010-1234-5678", VerificationPurpose.SIGN_UP));
        SmsVerification verification = repository.save(SmsVerification.issue(
                "01012345678", VerificationPurpose.SIGN_UP,
                new BCryptPasswordEncoder().encode("123456"), java.time.Instant.now().plusSeconds(300)));
        when(repository.findById(issued.verificationId())).thenReturn(Optional.of(verification));
        when(tokenHasher.hash(any(String.class))).thenReturn("token-hash");
        when(repository.findByVerificationTokenHash("token-hash")).thenReturn(Optional.of(verification));

        var confirmed = service.confirm(new SmsVerificationConfirmRequest(issued.verificationId(), "123456"));
        service.consume(confirmed.verificationToken(), "010-1234-5678", VerificationPurpose.SIGN_UP);

        assertThat(confirmed.verified()).isTrue();
        assertThatThrownBy(() -> service.consume(
                confirmed.verificationToken(), "01012345678", VerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void sendNormalizesMobilePhoneBeforeSavingAndSending() {
        when(repository.save(any(SmsVerification.class))).thenAnswer(invocation -> {
            SmsVerification verification = invocation.getArgument(0);
            ReflectionTestUtils.setField(verification, "id", 1L);
            return verification;
        });

        SmsVerificationResponse response = service.send(
                new SmsVerificationRequest("010 1234 5678", VerificationPurpose.SIGN_UP));

        assertThat(response.verificationId()).isEqualTo(1L);
        verify(smsSender).send(eq("01012345678"), any(String.class));
    }

    @Test
    void sendRejectsInvalidMobilePhoneBeforeSending() {
        for (String phone : List.of("abc", "011-1234-5678", "02-123-4567", "010-123-4567")) {
            assertThatThrownBy(() -> service.send(new SmsVerificationRequest(phone, VerificationPurpose.SIGN_UP)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
        }

        verifyNoInteractions(smsSender);
    }

    @Test
    void sendFailsWhenRateLimitIsExceeded() {
        when(repository.existsByPhoneNumberAndCreatedAtAfter(any(String.class), any())).thenReturn(true);

        assertThatThrownBy(() -> service.send(
                new SmsVerificationRequest("010-1234-5678", VerificationPurpose.SIGN_UP)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SMS_RATE_LIMITED);
    }

    @Test
    void sendFailsWhenSmsProviderFails() {
        org.mockito.Mockito.doThrow(new SmsSendException("failed", new RuntimeException()))
                .when(smsSender).send(any(String.class), any(String.class));

        assertThatThrownBy(() -> service.send(
                new SmsVerificationRequest("010-1234-5678", VerificationPurpose.SIGN_UP)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SMS_SEND_FAILED);
    }

    @Test
    void confirmFailsWhenCodeIsExpired() {
        SmsVerification verification = SmsVerification.issue(
                "01012345678", VerificationPurpose.SIGN_UP,
                new BCryptPasswordEncoder().encode("123456"), Instant.now().minusSeconds(1));
        when(repository.findById(1L)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.confirm(new SmsVerificationConfirmRequest(1L, "123456")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION);
    }

    @Test
    void confirmFailsAfterFiveWrongAttempts() {
        SmsVerification verification = SmsVerification.issue(
                "01012345678", VerificationPurpose.SIGN_UP,
                new BCryptPasswordEncoder().encode("123456"), Instant.now().plusSeconds(300));
        when(repository.findById(1L)).thenReturn(Optional.of(verification));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.confirm(new SmsVerificationConfirmRequest(1L, "000000")))
                    .isInstanceOf(CustomException.class);
        }

        assertThat(verification.getFailedAttempts()).isEqualTo(5);
        assertThatThrownBy(() -> service.confirm(new SmsVerificationConfirmRequest(1L, "123456")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION);
    }

    @Test
    void consumeFailsWhenPhoneOrPurposeDoesNotMatch() {
        SmsVerification verification = SmsVerification.issue(
                "01012345678", VerificationPurpose.SIGN_UP,
                new BCryptPasswordEncoder().encode("123456"), Instant.now().plusSeconds(300));
        verification.verify("token-hash", Instant.now().plusSeconds(900), Instant.now());
        when(tokenHasher.hash("verified-token")).thenReturn("token-hash");
        when(repository.findByVerificationTokenHash("token-hash")).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.consume(
                "verified-token", "01099998888", VerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.consume(
                "verified-token", "01012345678", VerificationPurpose.RESET_PASSWORD))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void consumeRejectsInvalidMobilePhone() {
        assertThatThrownBy(() -> service.consume("verified-token", "abc", VerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
    }
}
