package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VlmDescriptionJobWriter {
    private static final Logger log = LoggerFactory.getLogger(VlmDescriptionJobWriter.class);

    private final AlertEventDescriptionRepository repository;
    private final MediaObjectVerifier mediaObjectVerifier;

    @Value("${vlm.prompt-version:v1}")
    private String promptVersion;

    @Value("${vlm.model-name:mock-vlm}")
    private String vlmModelName;

    @Value("${vlm.max-retries:3}")
    private int maxRetries;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(AlertEvent event, VlmSourceSelector.VlmSource source) {
        String sourceKey = source.sourceKey();
        try {
            if (!mediaObjectVerifier.exists(sourceKey)) {
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("VLM media verification failed; enqueue skipped: eventId={}, sourceKey={}, error={}",
                    event.getEventId(), sourceKey, ex.getMessage());
            return;
        }

        boolean exists = repository.existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                source.sourceType(), sourceKey, promptVersion, vlmModelName);
        if (!exists) {
            repository.save(AlertEventDescription.builder()
                    .alertEvent(event)
                    .sourceAssetType(source.sourceType())
                    .sourceAssetKey(sourceKey)
                    .promptVersion(promptVersion)
                    .vlmModelName(vlmModelName)
                    .maxRetries(maxRetries)
                    .build());
        }
    }
}
