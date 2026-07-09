package com.strange.safety.vlm.service;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.company.repository.CompanyProfileRepository;
import com.strange.safety.facility.service.FacilityService;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.vlm.dto.SemanticSearchResultResponse;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import com.strange.safety.alert.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SemanticSearchService {
    private final AlertEventDescriptionRepository repository;
    private final EmbeddingService embeddingService;
    private final UserRepository userRepository;
    private final FacilityService facilityService;
    private final CompanyProfileRepository companyProfileRepository;
    private final S3Service s3Service;

    public List<SemanticSearchResultResponse> searchFacility(Long userId, Long facilityId, String query,
                                                             int topK, double minSimilarity,
                                                             LocalDateTime dateFrom, LocalDateTime dateTo,
                                                             Long cameraId, boolean excludeMock) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == Role.CORPORATE) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
        facilityService.getFacilityWithOwnerCheck(userId, facilityId);
        List<AlertEventDescription> rows = repository.findSearchableForFacility(
                facilityId, VlmJobStatus.SUCCESS, dateFrom, dateTo, cameraId, excludeMock);
        return rank(query, rows, topK, minSimilarity);
    }

    public List<SemanticSearchResultResponse> searchCompany(Long userId, Long companyProfileId, String query,
                                                            int topK, double minSimilarity,
                                                            LocalDateTime dateFrom, LocalDateTime dateTo,
                                                            Long cameraId, boolean excludeMock) {
        CompanyProfile profile = companyProfileRepository.findById(companyProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_PROFILE_NOT_FOUND));
        if (!profile.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FACILITY_ACCESS_DENIED);
        }
        List<AlertEventDescription> rows = repository.findSearchableForCompany(
                companyProfileId, VlmJobStatus.SUCCESS, dateFrom, dateTo, cameraId, excludeMock);
        return rank(query, rows, topK, minSimilarity);
    }

    private List<SemanticSearchResultResponse> rank(String query, List<AlertEventDescription> rows,
                                                    int topK, double minSimilarity) {
        double[] queryEmbedding = embeddingService.embed(query);
        return rows.stream()
                .map(row -> new ScoredDescription(row, embeddingService.cosineSimilarity(
                        queryEmbedding, embeddingService.decode(row.getDescriptionEmbedding()))))
                .filter(row -> row.score() >= minSimilarity)
                .sorted(Comparator.comparingDouble(ScoredDescription::score).reversed())
                .limit(Math.max(1, Math.min(topK, 50)))
                .map(row -> SemanticSearchResultResponse.from(
                        row.description().getAlertEvent(),
                        row.description().getVlmDescription(),
                        row.description().getVlmJson(),
                        row.score(),
                        keyframeUrls(row.description().getDeidentifiedKeyframeKeys())))
                .toList();
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

    private record ScoredDescription(AlertEventDescription description, double score) {
    }
}
