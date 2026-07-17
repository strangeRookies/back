package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.VlmSourceType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


@Service
@RequiredArgsConstructor
public class VlmDescriptionEnqueueService {
    private static final Logger log = LoggerFactory.getLogger(VlmDescriptionEnqueueService.class);

    private final VlmSourceSelector sourceSelector;
    private final VlmDescriptionJobWriter jobWriter;

    @Value("${vlm.enabled:true}")
    private boolean vlmEnabled = true;

    public void enqueueIfMediaExists(AlertEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            return;
        }
        if (!vlmEnabled) {
            return;
        }

        sourceSelector.select(event).ifPresent(source -> {
            String sourceKey = source.sourceKey();
            if (sourceKey == null || sourceKey.isBlank()) {
                return;
            }
            if (source.sourceType() == VlmSourceType.CLIP && !sourceKey.startsWith("clips/")) {
                return;
            }

            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch(event, source);
                    }
                });
                return;
            }
            dispatch(event, source);
        });
    }

    private void dispatch(AlertEvent event, VlmSourceSelector.VlmSource source) {
        try {
            jobWriter.enqueue(event, source);
        } catch (RuntimeException ex) {
            log.warn("VLM enqueue failed after primary transaction isolation: eventId={}, sourceKey={}, error={}",
                    event.getEventId(), source.sourceKey(), ex.getMessage());
        }
    }
}
