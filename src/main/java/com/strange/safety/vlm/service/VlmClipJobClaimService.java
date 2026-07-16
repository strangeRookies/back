package com.strange.safety.vlm.service;

import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VlmClipJobClaimService {

    private final AlertEventDescriptionRepository repository;

    @Value("${vlm.timeout-seconds:120}")
    private long timeoutSeconds;

    @Transactional
    public List<Long> claimClipJobIds(LocalDateTime now, int limit) {
        List<AlertEventDescription> claimed = repository.claimClipJobs(now, limit);
        List<Long> ids = new ArrayList<>(claimed.size());
        LocalDateTime lockedUntil = now.plusSeconds(timeoutSeconds + 30);
        for (AlertEventDescription job : claimed) {
            job.markProcessing(lockedUntil);
            repository.save(job);
            ids.add(job.getId());
        }
        return ids;
    }
}