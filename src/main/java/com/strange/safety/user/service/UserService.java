package com.strange.safety.user.service;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.user.dto.UpdatePasswordRequest;
import com.strange.safety.user.dto.UpdateProfileRequest;
import com.strange.safety.user.dto.UserProfileResponse;
import com.strange.safety.user.dto.UserResponse;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findActiveUser(userId));
    }

    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findActiveUser(userId));
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(email)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw new CustomException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
                });
        user.updateProfile(request.getName().trim(), email, request.getPhoneNumber());
    }

    @Transactional
    public void changePassword(Long userId, UpdatePasswordRequest request) {
        User user = findActiveUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.USER_INVALID_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        findActiveUser(userId).withdraw();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
