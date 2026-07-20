package com.strange.safety.vlm.embedding;

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
public class EmbeddingJobClaimService {
    private final AlertEventDescriptionRepository repository;

    @Value("${vlm.embedding-lock-seconds:120}")
    private long lockSeconds;

    @Transactional
    public List<Long> claimJobIds(LocalDateTime now, int limit) {
        List<AlertEventDescription> claimed = repository.claimEmbeddingJobs(now, limit);
        List<Long> ids = new ArrayList<>(claimed.size());
        LocalDateTime lockedUntil = now.plusSeconds(lockSeconds);
        for (AlertEventDescription job : claimed) {
            job.markEmbeddingProcessing(lockedUntil);
            repository.save(job);
            ids.add(job.getId());
        }
        return ids;
    }
}
