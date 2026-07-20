package com.strange.safety.push.service;

import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.repository.UserFacilityRepository;
import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.user.entity.UserStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushRecipientService {

    private final UserFacilityRepository userFacilityRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final PushDeviceRepository pushDeviceRepository;

    public List<PushRecipient> findRecipients(AlertPushRequestedEvent event) {
        List<Long> userIds;
        if ("FACILITY".equals(event.targetType())) {
            userIds = userFacilityRepository.findActiveUserIdsByFacilityId(
                    event.targetId(), List.of(AccessType.MANAGER, AccessType.VIEWER));
        } else if ("COMPANY".equals(event.targetType())) {
            userIds = companyProfileRepository.findById(event.targetId())
                    .filter(profile -> profile.getUser().getStatus() == UserStatus.ACTIVE)
                    .map(profile -> List.of(profile.getUser().getId()))
                    .orElseGet(List::of);
        } else {
            return List.of();
        }

        if (userIds.isEmpty()) {
            return List.of();
        }
        return pushDeviceRepository.findByUser_IdInAndActiveTrue(userIds).stream()
                .map(device -> new PushRecipient(device.getId(), device.getToken()))
                .toList();
    }
}
