package com.strange.safety.auth.service;

import com.strange.safety.auth.dto.PasswordResetRequest;
import com.strange.safety.auth.dto.PasswordResetSmsConfirmRequest;
import com.strange.safety.auth.dto.PasswordResetSmsRequest;
import com.strange.safety.auth.dto.PasswordResetSmsResponse;
import com.strange.safety.auth.dto.SmsVerificationRequest;
import com.strange.safety.auth.dto.SmsVerificationConfirmResponse;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.auth.session.RefreshTokenStore;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;
    private final SmsVerificationService smsVerificationService;

    public PasswordResetSmsResponse sendSms(PasswordResetSmsRequest request) {
        String email = normalizeEmail(request.email());
        String phone = smsVerificationService.normalizePhone(request.phone());
        findActiveUser(email, phone).ifPresent(user -> sendPasswordResetSms(phone));
        return new PasswordResetSmsResponse(SmsVerificationService.CODE_EXPIRATION_SECONDS);
    }

    public SmsVerificationConfirmResponse confirmSms(PasswordResetSmsConfirmRequest request) {
        String email = normalizeEmail(request.email());
        String phone = smsVerificationService.normalizePhone(request.phone());
        findActiveUser(email, phone)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION));
        return smsVerificationService.confirmLatest(phone, VerificationPurpose.RESET_PASSWORD, request.code());
    }

    public void resetPassword(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        String phone = smsVerificationService.normalizePhone(request.phone());
        User user = findActiveUser(email, phone)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION));
        smsVerificationService.consume(request.verificationToken(), phone, VerificationPurpose.RESET_PASSWORD);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenStore.revokeAllByUserId(user.getId());
    }

    private Optional<User> findActiveUser(String email, String phone) {
        return userRepository.findByEmailAndPhoneNumberAndStatus(email, phone, UserStatus.ACTIVE);
    }

    private void sendPasswordResetSms(String phone) {
        try {
            smsVerificationService.send(new SmsVerificationRequest(phone, VerificationPurpose.RESET_PASSWORD));
        } catch (CustomException exception) {
            log.warn("Password reset SMS request accepted without sending: {}", exception.getErrorCode().getCode());
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
