package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VlmDescriptionJobWriterTest {
    @Mock
    private AlertEventDescriptionRepository repository;
    @Mock
    private MediaObjectVerifier mediaObjectVerifier;

    private VlmDescriptionJobWriter writer;

    @BeforeEach
    void setUp() {
        writer = new VlmDescriptionJobWriter(repository, mediaObjectVerifier);
        ReflectionTestUtils.setField(writer, "promptVersion", "prompt-v2");
        ReflectionTestUtils.setField(writer, "vlmModelName", "gemini-vlm");
        ReflectionTestUtils.setField(writer, "maxRetries", 3);
    }

    @Test
    void usesRequiresNewTransactionBoundary() throws Exception {
        Method method = VlmDescriptionJobWriter.class.getMethod(
                "enqueue", AlertEvent.class, VlmSourceSelector.VlmSource.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void verifiesMediaAndDeduplicatesByCompleteContractTuple() {
        AlertEvent event = event();
        VlmSourceSelector.VlmSource source = source("clips/a.mp4");
        when(mediaObjectVerifier.exists("clips/a.mp4")).thenReturn(true);
        when(repository.existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                VlmSourceType.CLIP, "clips/a.mp4", "prompt-v2", "gemini-vlm")).thenReturn(false);

        writer.enqueue(event, source);

        ArgumentCaptor<AlertEventDescription> captor = ArgumentCaptor.forClass(AlertEventDescription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAlertEvent()).isSameAs(event);
        assertThat(captor.getValue().getSourceAssetType()).isEqualTo(VlmSourceType.CLIP);
        assertThat(captor.getValue().getSourceAssetKey()).isEqualTo("clips/a.mp4");
        assertThat(captor.getValue().getPromptVersion()).isEqualTo("prompt-v2");
        assertThat(captor.getValue().getVlmModelName()).isEqualTo("gemini-vlm");
    }

    @Test
    void skipsExistingCompleteDedupeTuple() {
        when(mediaObjectVerifier.exists("clips/a.mp4")).thenReturn(true);
        when(repository.existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                VlmSourceType.CLIP, "clips/a.mp4", "prompt-v2", "gemini-vlm")).thenReturn(true);

        writer.enqueue(event(), source("clips/a.mp4"));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenVerifierReturnsFalseOrThrows() {
        when(mediaObjectVerifier.exists("clips/missing.mp4")).thenReturn(false);
        writer.enqueue(event(), source("clips/missing.mp4"));
        verifyNoInteractions(repository);

        when(mediaObjectVerifier.exists("clips/a.mp4")).thenThrow(new IllegalStateException("S3 unavailable"));
        writer.enqueue(event(), source("clips/a.mp4"));
        verifyNoInteractions(repository);
    }

    private AlertEvent event() {
        return AlertEvent.builder().eventId("event-1").build();
    }

    private VlmSourceSelector.VlmSource source(String key) {
        return new VlmSourceSelector.VlmSource(VlmSourceType.CLIP, key);
    }
}
