package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class VlmDescriptionEnqueueServiceTest {
    @Mock
    private VlmDescriptionJobWriter jobWriter;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void requiresEventIdAndSelectedSourceKey() {
        VlmDescriptionEnqueueService service = service();

        service.enqueueIfMediaExists(event(null, "clips/a.mp4"));
        service.enqueueIfMediaExists(event("event-1", null));

        verifyNoInteractions(jobWriter);
    }

    @Test
    void rejectsClipOutsideClipsPrefix() {
        service().enqueueIfMediaExists(event("event-1", "uploads/a.mp4"));

        verifyNoInteractions(jobWriter);
    }

    @Test
    void dispatchesImmediatelyWithoutPrimaryTransaction() {
        AlertEvent event = event("event-1", "clips/a.mp4");

        service().enqueueIfMediaExists(event);

        verify(jobWriter).enqueue(event,
                new VlmSourceSelector.VlmSource(com.strange.safety.vlm.entity.VlmSourceType.CLIP, "clips/a.mp4"));
    }

    @Test
    void defersUntilAfterCommitAndAbsorbsIsolatedPersistenceFailure() {
        AlertEvent event = event("event-1", "clips/a.mp4");
        VlmSourceSelector.VlmSource source = new VlmSourceSelector.VlmSource(
                com.strange.safety.vlm.entity.VlmSourceType.CLIP, "clips/a.mp4");
        doThrow(new IllegalStateException("database unavailable")).when(jobWriter).enqueue(event, source);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service().enqueueIfMediaExists(event);

        verifyNoInteractions(jobWriter);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        assertThatCode(synchronizations.getFirst()::afterCommit).doesNotThrowAnyException();
        verify(jobWriter).enqueue(event, source);
    }
    @Test
    void skipsEnqueueWhenVlmDisabled() {
        AlertEvent event = event("event-1", "clips/a.mp4");
        VlmDescriptionEnqueueService service = service(false);

        service.enqueueIfMediaExists(event);

        verifyNoInteractions(jobWriter);
    }

    private VlmDescriptionEnqueueService service() {
        return service(true);
    }

    private VlmDescriptionEnqueueService service(boolean enabled) {
        VlmDescriptionEnqueueService service = new VlmDescriptionEnqueueService(new VlmSourceSelector(), jobWriter);
        ReflectionTestUtils.setField(service, "vlmEnabled", enabled);
        return service;
    }

    private AlertEvent event(String eventId, String clipObjectKey) {
        return AlertEvent.builder()
                .eventId(eventId)
                .clipObjectKey(clipObjectKey)
                .build();
    }
}
