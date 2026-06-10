package com.strange.safety.auth.service;

import com.strange.safety.auth.dto.*;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SmsVerificationService {

    private static final long CODE_EXPIRATION_SECONDS = 300;
    private static final long TOKEN_EXPIRATION_SECONDS = 900;
    private static final String MOBILE_PHONE_PATTERN = "^010\\d{8}$";

    private final SmsVerificationRepository smsVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenHasher tokenHasher;
    private final SmsSender smsSender;
    private final SmsCodeGenerator smsCodeGenerator;
    private final SmsProperties smsProperties;

    public SmsVerificationResponse send(SmsVerificationRequest request) {
        String phoneNumber = normalizePhone(request.phone());
        validateRateLimit(phoneNumber);

        String code = smsCodeGenerator.generate();
        String message = smsProperties.getMessage().getVerificationTemplate().replace("{code}", code);
        sendMessage(phoneNumber, message);

        SmsVerification verification = SmsVerification.issue(
                phoneNumber, request.purpose(),
                passwordEncoder.encode(code),
                Instant.now().plusSeconds(CODE_EXPIRATION_SECONDS)
        );
        SmsVerification saved = smsVerificationRepository.save(verification);
        return new SmsVerificationResponse(saved.getId(), CODE_EXPIRATION_SECONDS);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public SmsVerificationConfirmResponse confirm(SmsVerificationConfirmRequest request) {
        SmsVerification verification = smsVerificationRepository.findById(request.verificationId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION));
        Instant now = Instant.now();
        if (!verification.canConfirm(now)) {
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION);
        }
        if (!passwordEncoder.matches(request.code(), verification.getCodeHash())) {
            verification.recordFailure();
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION);
        }
        String token = UUID.randomUUID().toString();
        verification.verify(tokenHasher.hash(token), now.plusSeconds(TOKEN_EXPIRATION_SECONDS), now);
        return new SmsVerificationConfirmResponse(true, token);
    }

    public void consume(String token, String phone, VerificationPurpose purpose) {
        String phoneNumber = normalizePhone(phone);
        SmsVerification verification = smsVerificationRepository
                .findByVerificationTokenHash(tokenHasher.hash(token))
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION));
        Instant now = Instant.now();
        if (!verification.canUse(phoneNumber, purpose, now)) {
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION);
        }
        verification.use(now);
    }

    public String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (!normalized.matches(MOBILE_PHONE_PATTERN)) {
            throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        }
        return normalized;
    }

    private void validateRateLimit(String phoneNumber) {
        SmsProperties.RateLimit rateLimit = smsProperties.getRateLimit();
        LocalDateTime now = LocalDateTime.now();
        if (rateLimit.getResendCooldownSeconds() > 0
                && smsVerificationRepository.existsByPhoneNumberAndCreatedAtAfter(
                phoneNumber, now.minusSeconds(rateLimit.getResendCooldownSeconds()))) {
            throw new CustomException(ErrorCode.SMS_RATE_LIMITED);
        }
        if (rateLimit.getDailyLimitPerPhone() > 0
                && smsVerificationRepository.countByPhoneNumberAndCreatedAtAfter(phoneNumber, now.minusDays(1))
                >= rateLimit.getDailyLimitPerPhone()) {
            throw new CustomException(ErrorCode.SMS_RATE_LIMITED);
        }
    }

    private void sendMessage(String phoneNumber, String message) {
        try {
            smsSender.send(phoneNumber, message);
        } catch (SmsSendException exception) {
            throw new CustomException(ErrorCode.SMS_SEND_FAILED);
        }
    }

}
