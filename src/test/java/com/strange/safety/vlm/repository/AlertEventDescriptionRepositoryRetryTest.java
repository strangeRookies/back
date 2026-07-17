package com.strange.safety.vlm.repository;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AlertEventDescriptionRepositoryRetryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AlertEventDescriptionRepository repository;

    @Test
    void claimExcludesPendingJobUntilNextAttemptAt() {
        Scenario scenario = Scenario.builder()
                .scenarioType(ScenarioType.COLLAPSE)
                .description("collapse")
                .build();
        timestamps(scenario);
        entityManager.persist(scenario);
        AlertEvent event = AlertEvent.builder()
                .scenario(scenario)
                .confidenceScore(0.9f)
                .severity(AlertSeverity.CRITICAL)
                .detectedAt(LocalDateTime.of(2026, 7, 17, 12, 0))
                .eventId("retry-claim-event")
                .build();
        timestamps(event);
        entityManager.persist(event);
        AlertEventDescription job = AlertEventDescription.builder()
                .alertEvent(event)
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("retry-claim.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(3)
                .build();
        timestamps(job);
        LocalDateTime retryAt = LocalDateTime.of(2026, 7, 17, 12, 5);
        job.markFailed("rate limited", retryAt);
        entityManager.persistAndFlush(job);
        entityManager.clear();

        assertTrue(repository.claimClipJobs(retryAt.minusSeconds(1), 10).isEmpty());
        assertEquals(1, repository.claimClipJobs(retryAt, 10).size());
    }

    @Test
    void embeddingClaimExcludesPendingJobUntilEmbeddingNextAttemptAt() {
        Scenario scenario = Scenario.builder()
                .scenarioType(ScenarioType.COLLAPSE)
                .description("collapse")
                .build();
        timestamps(scenario);
        entityManager.persist(scenario);
        AlertEvent event = AlertEvent.builder()
                .scenario(scenario)
                .confidenceScore(0.9f)
                .severity(AlertSeverity.CRITICAL)
                .detectedAt(LocalDateTime.of(2026, 7, 17, 12, 0))
                .eventId("embedding-retry-claim-event")
                .build();
        timestamps(event);
        entityManager.persist(event);
        AlertEventDescription job = AlertEventDescription.builder()
                .alertEvent(event)
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("embedding-retry-claim.mp4")
                .promptVersion("v1")
                .vlmModelName("gemini")
                .maxRetries(3)
                .build();
        timestamps(job);
        job.markSuccess("{}", "description", null, null, null, false);
        LocalDateTime retryAt = LocalDateTime.of(2026, 7, 17, 12, 5);
        job.markEmbeddingFailed("rate limited", retryAt);
        entityManager.persistAndFlush(job);
        entityManager.clear();

        assertTrue(repository.claimEmbeddingJobs(retryAt.minusSeconds(1), 10).isEmpty());
        assertEquals(1, repository.claimEmbeddingJobs(retryAt, 10).size());
    }

    private void timestamps(Object entity) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);
        ReflectionTestUtils.setField(entity, "createdAt", now);
        ReflectionTestUtils.setField(entity, "updatedAt", now);
    }
}
