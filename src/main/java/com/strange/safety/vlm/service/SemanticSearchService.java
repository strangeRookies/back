package com.strange.safety.vlm.service;

import com.strange.safety.alert.service.S3Service;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.Snapshot;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.vlm.dto.SemanticSearchResultResponse;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.vlm.repository.PgVectorSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SemanticSearchService {
    private static final int MAX_QUERY_LENGTH = 1_000;

    private final AlertEventDescriptionRepository repository;
    private final PgVectorSearchRepository pgVectorRepository;
    private final EmbeddingService embeddingService;
    private final UserRepository userRepository;
    private final FacilityService facilityService;
    private final CompanyProfileRepository companyProfileRepository;
    private final CameraRepository cameraRepository;
    private final CorporateCameraRepository corporateCameraRepository;
    private final S3Service s3Service;

    @Value("${vlm.pgvector.enabled:${VLM_PGVECTOR_ENABLED:false}}")
    private boolean pgVectorEnabled;

    public List<SemanticSearchResultResponse> searchFacility(Long userId, Long facilityId, String query,
                                                             int topK, double minSimilarity,
                                                             LocalDateTime dateFrom, LocalDateTime dateTo,
                                                             Long cameraId, boolean excludeMock) {
        validateQuery(query, topK, minSimilarity, dateFrom, dateTo);
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == Role.CORPORATE) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);
        validateFacilityCamera(cameraId, facilityId);
        if (pgVectorEnabled) {
            return pgVectorFacility(facilityId, query.trim(), topK, minSimilarity,
                    dateFrom, dateTo, cameraId, excludeMock);
        }
        List<AlertEventDescription> rows = repository.findSearchableForFacility(
                facilityId, VlmJobStatus.SUCCESS, dateFrom, dateTo, cameraId, excludeMock);
        return rankInMemory(query.trim(), rows, topK, minSimilarity);
    }

    public List<SemanticSearchResultResponse> searchCompany(Long userId, Long companyProfileId, String query,
                                                            int topK, double minSimilarity,
                                                            LocalDateTime dateFrom, LocalDateTime dateTo,
                                                            Long cameraId, boolean excludeMock) {
        validateQuery(query, topK, minSimilarity, dateFrom, dateTo);
        CompanyProfile profile = companyProfileRepository.findById(companyProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
        if (!profile.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
        validateCompanyCamera(cameraId, companyProfileId);
        if (pgVectorEnabled) {
            return pgVectorCompany(companyProfileId, query.trim(), topK, minSimilarity,
                    dateFrom, dateTo, cameraId, excludeMock);
        }
        List<AlertEventDescription> rows = repository.findSearchableForCompany(
                companyProfileId, VlmJobStatus.SUCCESS, dateFrom, dateTo, cameraId, excludeMock);
        return rankInMemory(query.trim(), rows, topK, minSimilarity);
    }

    private List<SemanticSearchResultResponse> pgVectorFacility(long facilityId, String query, int topK,
                                                                double minSimilarity, LocalDateTime dateFrom,
                                                                LocalDateTime dateTo, Long cameraId,
                                                                boolean excludeMock) {
        String embedding = queryEmbedding(query);
        return hydrate(pgVectorRepository.searchFacility(
                facilityId, embedding, embeddingService.embeddingModelName(), minSimilarity, topK,
                dateFrom, dateTo, cameraId, excludeMock));
    }

    private List<SemanticSearchResultResponse> pgVectorCompany(long companyProfileId, String query, int topK,
                                                               double minSimilarity, LocalDateTime dateFrom,
                                                               LocalDateTime dateTo, Long cameraId,
                                                               boolean excludeMock) {
        String embedding = queryEmbedding(query);
        return hydrate(pgVectorRepository.searchCompany(
                companyProfileId, embedding, embeddingService.embeddingModelName(), minSimilarity, topK,
                dateFrom, dateTo, cameraId, excludeMock));
    }

    private String queryEmbedding(String query) {
        double[] embedding = embeddingService.embed(query);
        if (embedding.length != VlmIndexPayloadParser.EMBEDDING_DIMENSION
                || Arrays.stream(embedding).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalStateException("Query embedding provider must return 768 finite values");
        }
        return embeddingService.encode(embedding);
    }

    private List<SemanticSearchResultResponse> hydrate(List<PgVectorSearchRepository.ScoredId> scoredIds) {
        if (scoredIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AlertEventDescription> rows = repository.findAllById(
                        scoredIds.stream().map(PgVectorSearchRepository.ScoredId::id).toList())
                .stream().collect(Collectors.toMap(AlertEventDescription::getId, Function.identity()));
        return scoredIds.stream()
                .map(scored -> toResponse(rows.get(scored.id()), scored.similarity()))
                .toList();
    }

    private List<SemanticSearchResultResponse> rankInMemory(String query, List<AlertEventDescription> rows,
                                                            int topK, double minSimilarity) {
        double[] queryEmbedding = embeddingService.embed(query);
        return rows.stream()
                .map(row -> new ScoredDescription(row, embeddingService.cosineSimilarity(
                        queryEmbedding, embeddingService.decode(row.getDescriptionEmbedding()))))
                .filter(row -> row.score() >= minSimilarity)
                .sorted(Comparator.comparingDouble(ScoredDescription::score).reversed())
                .limit(topK)
                .map(row -> toResponse(row.description(), row.score()))
                .toList();
    }

    private SemanticSearchResultResponse toResponse(AlertEventDescription row, double score) {
        if (row == null) {
            throw new IllegalStateException("Semantic search projection references a missing description");
        }
        AlertEvent event = row.getAlertEvent();
        return SemanticSearchResultResponse.from(
                event, row.getVlmDescription(), row.getVlmJson(), score,
                keyframeUrls(row.getDeidentifiedKeyframeKeys()),
                presignedSnapshotUrl(event));
    }

    private void validateQuery(String query, int topK, double minSimilarity,
                               LocalDateTime dateFrom, LocalDateTime dateTo) {
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_LENGTH
                || topK < 1 || topK > 50
                || !Double.isFinite(minSimilarity) || minSimilarity < 0 || minSimilarity > 1
                || (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))) {
            throw new CustomException(ErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private void validateFacilityCamera(Long cameraId, Long facilityId) {
        if (cameraId == null) {
            return;
        }
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        if (!camera.getFacility().getId().equals(facilityId)) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
    }

    private void validateCompanyCamera(Long cameraId, Long companyProfileId) {
        if (cameraId == null) {
            return;
        }
        CorporateCamera camera = corporateCameraRepository.findById(cameraId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMERA_NOT_FOUND));
        if (!camera.getCompanyProfile().getId().equals(companyProfileId)) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
    }

    private List<String> keyframeUrls(String encodedKeys) {
        if (encodedKeys == null || encodedKeys.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encodedKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .map(s3Service::generatePresignedUrl)
                .toList();
    }

    private String presignedSnapshotUrl(AlertEvent event) {
        if (event == null || event.getSnapshots() == null || event.getSnapshots().isEmpty()) {
            return null;
        }
        Snapshot first = event.getSnapshots().get(0);
        if (first.getSnapshotUrl() == null || first.getSnapshotUrl().isBlank()) {
            return null;
        }
        return s3Service.generatePresignedUrl(first.getSnapshotUrl());
    }

    private record ScoredDescription(AlertEventDescription description, double score) {
    }
}
