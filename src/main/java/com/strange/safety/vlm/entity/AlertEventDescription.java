package com.strange.safety.vlm.entity;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alert_event_descriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alert_description_source",
                columnNames = {"source_asset_type", "source_asset_key", "prompt_version", "vlm_model_name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertEventDescription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_event_description_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_event_id", nullable = false)
    private AlertEvent alertEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VlmJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_asset_type", nullable = false, length = 20)
    private VlmSourceType sourceAssetType;

    @Column(name = "source_asset_key", nullable = false, length = 512)
    private String sourceAssetKey;

    @Column(name = "vlm_json", columnDefinition = "text")
    private String vlmJson;

    @Column(name = "vlm_description", columnDefinition = "text")
    private String vlmDescription;

    @Column(name = "description_embedding", columnDefinition = "text")
    private String descriptionEmbedding;

    @Column(name = "deidentified_keyframe_keys", columnDefinition = "text")
    private String deidentifiedKeyframeKeys;

    @Column(name = "vlm_model_name", nullable = false, length = 80)
    private String vlmModelName;

    @Column(name = "embedding_model_name", length = 80)
    private String embeddingModelName;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "mock_result", nullable = false)
    private boolean mockResult;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 20)
    private EmbeddingJobStatus embeddingStatus;

    @Column(name = "embedding_retry_count", nullable = false)
    private int embeddingRetryCount;

    @Column(name = "embedding_max_retries", nullable = false)
    private int embeddingMaxRetries;

    @Column(name = "embedding_locked_until")
    private LocalDateTime embeddingLockedUntil;

    @Column(name = "embedding_next_attempt_at")
    private LocalDateTime embeddingNextAttemptAt;

    @Column(name = "embedding_error_message", columnDefinition = "text")
    private String embeddingErrorMessage;


    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Builder
    private AlertEventDescription(AlertEvent alertEvent, VlmSourceType sourceAssetType,
                                  String sourceAssetKey, String promptVersion,
                                  String vlmModelName, int maxRetries) {
        this.alertEvent = alertEvent;
        this.status = VlmJobStatus.PENDING;
        this.sourceAssetType = sourceAssetType;
        this.sourceAssetKey = sourceAssetKey;
        this.promptVersion = promptVersion;
        this.vlmModelName = vlmModelName;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.embeddingStatus = EmbeddingJobStatus.PENDING;
        this.embeddingRetryCount = 0;
        this.embeddingMaxRetries = maxRetries;
        this.mockResult = false;
    }

    public void markProcessing(LocalDateTime lockedUntil) {
        this.status = VlmJobStatus.PROCESSING;
        this.lockedUntil = lockedUntil;
        this.nextAttemptAt = null;
        this.errorMessage = null;
    }

    public void markSuccess(String vlmJson, String vlmDescription, String descriptionEmbedding,
                            String deidentifiedKeyframeKeys, String embeddingModelName,
                            boolean mockResult) {
        this.status = VlmJobStatus.SUCCESS;
        this.vlmJson = vlmJson;
        this.vlmDescription = vlmDescription;
        this.descriptionEmbedding = descriptionEmbedding;
        this.deidentifiedKeyframeKeys = deidentifiedKeyframeKeys;
        this.embeddingModelName = embeddingModelName;
        this.mockResult = mockResult;
        this.embeddingStatus = descriptionEmbedding == null || descriptionEmbedding.isBlank()
                ? EmbeddingJobStatus.PENDING
                : EmbeddingJobStatus.SUCCESS;
        this.embeddingLockedUntil = null;
        this.embeddingNextAttemptAt = null;
        this.embeddingErrorMessage = null;
        this.lockedUntil = null;
        this.nextAttemptAt = null;
        this.errorMessage = null;
    }

    public void markSkipped(String errorMessage) {
        this.status = VlmJobStatus.SKIPPED;
        this.lockedUntil = null;
        this.nextAttemptAt = null;
        this.errorMessage = errorMessage;
    }

    public void markFailed(String errorMessage, LocalDateTime nextAttemptAt) {
        this.retryCount += 1;
        this.status = this.retryCount >= this.maxRetries ? VlmJobStatus.FAILED : VlmJobStatus.PENDING;
        this.lockedUntil = null;
        this.nextAttemptAt = this.status == VlmJobStatus.PENDING ? nextAttemptAt : null;
        this.errorMessage = errorMessage;
    }

    public void markEmbeddingProcessing(LocalDateTime lockedUntil) {
        this.embeddingStatus = EmbeddingJobStatus.PROCESSING;
        this.embeddingLockedUntil = lockedUntil;
        this.embeddingNextAttemptAt = null;
        this.embeddingErrorMessage = null;
    }

    public void markEmbeddingSuccess(String descriptionEmbedding, String embeddingModelName) {
        this.descriptionEmbedding = descriptionEmbedding;
        this.embeddingModelName = embeddingModelName;
        this.embeddingStatus = EmbeddingJobStatus.SUCCESS;
        this.embeddingLockedUntil = null;
        this.embeddingNextAttemptAt = null;
        this.embeddingErrorMessage = null;
    }

    public void markEmbeddingFailed(String errorMessage, LocalDateTime nextAttemptAt) {
        this.embeddingRetryCount += 1;
        this.embeddingStatus = this.embeddingRetryCount >= this.embeddingMaxRetries
                ? EmbeddingJobStatus.FAILED
                : EmbeddingJobStatus.PENDING;
        this.embeddingLockedUntil = null;
        this.embeddingNextAttemptAt = this.embeddingStatus == EmbeddingJobStatus.PENDING ? nextAttemptAt : null;
        this.embeddingErrorMessage = errorMessage;
    }

    public void markFailed(String errorMessage) {
        markFailed(errorMessage, LocalDateTime.now());
    }
}
