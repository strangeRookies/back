package com.strange.safety.user.service;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.Facility;
import com.strange.safety.facility.repository.FacilityRepository;
import com.strange.safety.user.dto.AdminMemberUpdateRequest;
import com.strange.safety.user.dto.AdminUserResponse;
import com.strange.safety.user.dto.UpdatePasswordRequest;
import com.strange.safety.user.dto.UpdateProfileRequest;
import com.strange.safety.user.dto.UserProfileResponse;
import com.strange.safety.user.dto.UserResponse;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import com.strange.safety.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final CorporateCameraRepository corporateCameraRepository;
    private final FacilityRepository facilityRepository;
    private final CameraRepository cameraRepository;
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

    public Page<AdminUserResponse> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findByRoleInAndStatusNot(
                List.of(Role.INDIVIDUAL, Role.CORPORATE), UserStatus.WITHDRAWN, pageable);

        List<Long> allUserIds = users.stream().map(User::getId).toList();
        List<Long> corporateUserIds = users.stream()
                .filter(u -> u.getRole() == Role.CORPORATE)
                .map(User::getId)
                .toList();
        List<Long> individualUserIds = users.stream()
                .filter(u -> u.getRole() == Role.INDIVIDUAL)
                .map(User::getId)
                .toList();

        Map<Long, CompanyProfile> profileMap = corporateUserIds.isEmpty()
                ? Map.of()
                : companyProfileRepository.findByUserIdIn(corporateUserIds).stream()
                        .collect(Collectors.toMap(cp -> cp.getUser().getId(), cp -> cp));

        Map<Long, Integer> corporateCameraCountByProfileId;
        if (profileMap.isEmpty()) {
            corporateCameraCountByProfileId = Map.of();
        } else {
            List<Long> companyProfileIds = profileMap.values().stream()
                    .map(CompanyProfile::getId).toList();
            corporateCameraCountByProfileId = corporateCameraRepository
                    .countCamerasByCompanyProfileIds(companyProfileIds)
                    .stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> ((Number) row[1]).intValue()
                    ));
        }

        Map<Long, String> individualRegions = individualUserIds.isEmpty()
                ? Map.of()
                : facilityRepository.findDistrictsByUserIds(individualUserIds, AccessType.MANAGER)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row[0]).longValue(),
                                row -> row[1] != null ? (String) row[1] : "",
                                (first, second) -> first
                        ));

        Map<Long, Integer> individualCameraCounts = allUserIds.isEmpty()
                ? Map.of()
                : cameraRepository.countCamerasByUserIds(allUserIds, AccessType.MANAGER)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row[0]).longValue(),
                                row -> ((Number) row[1]).intValue()
                        ));

        return users.map(user -> {
            if (user.getRole() == Role.CORPORATE) {
                CompanyProfile cp = profileMap.get(user.getId());
                int cameraCount = cp != null
                        ? corporateCameraCountByProfileId.getOrDefault(cp.getId(), 0)
                        : 0;
                return AdminUserResponse.from(
                        user,
                        cp != null ? cp.getCompanyName() : user.getName(),
                        cp != null ? cp.getManagerName() : null,
                        cp != null ? cp.getManagerContact() : user.getPhoneNumber(),
                        cp != null ? cp.getDistrict() : null,
                        cameraCount
                );
            } else {
                return AdminUserResponse.from(
                        user,
                        user.getName(),
                        null,
                        user.getPhoneNumber(),
                        individualRegions.getOrDefault(user.getId(), null),
                        individualCameraCounts.getOrDefault(user.getId(), 0)
                );
            }
        });
    }

    @Transactional
    public void updateAdminMember(Long userId, AdminMemberUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getRole() == Role.CORPORATE) {
            CompanyProfile companyProfile = companyProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            companyProfile.update(request.name(), request.representative(), request.region(), request.contact());
        } else {
            user.updateContactInfo(request.name(), request.contact());
            List<Facility> facilities = facilityRepository.findActiveByManagerUserId(userId, AccessType.MANAGER);
            if (!facilities.isEmpty()) {
                facilities.get(0).updateDistrict(request.region());
            }
        }

        UserStatus newStatus;
        try {
            newStatus = UserStatus.valueOf(request.status());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        }
        if (newStatus != UserStatus.ACTIVE && newStatus != UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        }
        user.updateStatus(newStatus);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
