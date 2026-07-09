package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VlmDescriptionEnqueueService {
    private final AlertEventDescriptionRepository repository;
    private final VlmSourceSelector sourceSelector = new VlmSourceSelector();

    @Value("${vlm.prompt-version:v1}")
    private String promptVersion;

    @Value("${vlm.model-name:mock-vlm}")
    private String vlmModelName;

    @Value("${vlm.max-retries:3}")
    private int maxRetries;

    @Transactional
    public void enqueueIfMediaExists(AlertEvent event) {
        sourceSelector.select(event).ifPresent(source -> {
            boolean exists = repository.existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                    source.sourceType(), source.sourceKey(), promptVersion, vlmModelName);
            if (!exists) {
                repository.save(AlertEventDescription.builder()
                        .alertEvent(event)
                        .sourceAssetType(source.sourceType())
                        .sourceAssetKey(source.sourceKey())
                        .promptVersion(promptVersion)
                        .vlmModelName(vlmModelName)
                        .maxRetries(maxRetries)
                        .build());
            }
        });
    }
}
