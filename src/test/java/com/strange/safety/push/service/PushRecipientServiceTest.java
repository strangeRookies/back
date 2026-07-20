package com.strange.safety.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.push.entity.PushDevice;
import com.strange.safety.push.entity.PushPlatform;
import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushRecipientServiceTest {

    @Mock UserFacilityRepository userFacilityRepository;
    @Mock CompanyProfileRepository companyProfileRepository;
    @Mock PushDeviceRepository pushDeviceRepository;

    @Test
    void facilityTargetsActiveManagersAndViewers() {
        PushDevice device = device(20L, user("user@example.com"), "token");
        when(userFacilityRepository.findActiveUserIdsByFacilityId(
                10L, List.of(AccessType.MANAGER, AccessType.VIEWER))).thenReturn(List.of(1L));
        when(pushDeviceRepository.findByUser_IdInAndActiveTrue(List.of(1L))).thenReturn(List.of(device));
        PushRecipientService service = service();

        List<PushRecipient> recipients = service.findRecipients(event("FACILITY", 10L));

        assertThat(recipients).containsExactly(new PushRecipient(20L, "token"));
    }

    @Test
    void companyTargetsItsActiveProfileUser() {
        User user = user("corporate@example.com");
        ReflectionTestUtils.setField(user, "id", 2L);
        CompanyProfile profile = CompanyProfile.builder()
                .user(user)
                .companyName("회사")
                .businessRegistrationNumber("1234567890")
                .build();
        PushDevice device = device(30L, user, "corp-token");
        when(companyProfileRepository.findById(11L)).thenReturn(Optional.of(profile));
        when(pushDeviceRepository.findByUser_IdInAndActiveTrue(List.of(2L))).thenReturn(List.of(device));
        PushRecipientService service = service();

        List<PushRecipient> recipients = service.findRecipients(event("COMPANY", 11L));

        assertThat(recipients).containsExactly(new PushRecipient(30L, "corp-token"));
        verify(pushDeviceRepository).findByUser_IdInAndActiveTrue(List.of(2L));
    }

    private PushRecipientService service() {
        return new PushRecipientService(
                userFacilityRepository, companyProfileRepository, pushDeviceRepository);
    }

    private AlertPushRequestedEvent event(String targetType, Long targetId) {
        return new AlertPushRequestedEvent(
                100L, 30L, "cam", "camera", targetType, targetId,
                "FALL_BED", "CRITICAL", Instant.parse("2026-07-20T05:30:00Z"));
    }

    private User user(String email) {
        return User.create(email, "hash", "사용자", "01012345678", Role.INDIVIDUAL);
    }

    private PushDevice device(Long id, User user, String token) {
        PushDevice device = PushDevice.create(user, token, "device", PushPlatform.ANDROID, "1.0.0");
        ReflectionTestUtils.setField(device, "id", id);
        return device;
    }
}
