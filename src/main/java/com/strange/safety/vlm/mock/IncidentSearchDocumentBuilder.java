package com.strange.safety.vlm.mock;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class IncidentSearchDocumentBuilder {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public SearchDocument build(Incident incident, VlmAnalysisResult result, EmbeddingRepository embeddingRepository) {
        String timeline = incident.events().stream()
                .map(event -> event.eventType().name())
                .collect(Collectors.joining(", "));
        String text = """
                발생 시간: %s
                장소: %s
                카메라: %s
                초기 이벤트: %s
                후속 이벤트: %s
                사고 후 자세: %s
                움직임: %s
                누워 있던 시간: 약 %.0f초
                회복 여부: %s
                사람 특징: 검색용 모의 묘사, %s 상의, %s 하의
                요약: %s
                """.formatted(
                incident.detectedAt().format(FORMATTER),
                incident.locationName(),
                incident.cameraLoginId(),
                incident.events().getFirst().eventType().name(),
                timeline,
                result.postEventPosture(),
                result.movementAfterEvent(),
                result.estimatedLyingDurationSec(),
                result.recoveryObserved() ? "recovered" : "not recovered",
                result.personDescription().topColor(),
                result.personDescription().bottomColor(),
                result.summary()
        );
        return new SearchDocument(incident.incidentId(), text, embeddingRepository.embed(text));
    }
}
