package com.strange.safety.vlm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.strange.safety.vlm.dto.VlmIndexPayload;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class VlmIndexPayloadParser {
    public static final String SCHEMA_VERSION = "vlm-index-payload-v1";
    public static final int EMBEDDING_DIMENSION = 768;
    private static final int MAX_DOCUMENT_LENGTH = 65_536;
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final String VLM_RESULT_SCHEMA_VERSION = "vlm-result-v1";
    private static final Set<String> VLM_RESULT_FIELDS = Set.of(
            "schema_version",
            "incident_id",
            "visual_event_type",
            "people_count",
            "korean_search_keywords",
            "detailed_description_ko",
            "frame_count",
            "provider",
            "is_mock"
    );

    private final ObjectMapper strictMapper;

    public VlmIndexPayloadParser(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    public VlmIndexPayload parseAndValidate(String stdout, Expected expected) throws JsonProcessingException {
        VlmIndexPayload payload = strictMapper.readValue(stdout, VlmIndexPayload.class);
        require(SCHEMA_VERSION.equals(payload.schemaVersion()), "Unsupported VLM payload schema");
        require(expected.incidentId().equals(payload.incidentId()), "Envelope incident ID mismatch");
        require(expected.cameraLoginId().equals(payload.cameraLoginId()), "Envelope camera ID mismatch");
        require(expected.capturedAt().toInstant().equals(payload.capturedAt().toInstant()),
                "Captured timestamp mismatch");

        JsonNode result = payload.vlmResult();
        require(result != null && result.isObject(), "vlm_result must be an object");
        Set<String> resultFields = new HashSet<>();
        result.fieldNames().forEachRemaining(resultFields::add);
        require(resultFields.equals(VLM_RESULT_FIELDS), "vlm_result fields do not match vlm-result-v1");
        require(VLM_RESULT_SCHEMA_VERSION.equals(requiredText(result, "schema_version")),
                "Unsupported VLM result schema");
        require(expected.incidentId().equals(requiredText(result, "incident_id")), "VLM incident ID mismatch");
        require(validText(result, "visual_event_type", MAX_KEYWORD_LENGTH), "VLM visual_event_type is invalid");
        JsonNode peopleCount = result.get("people_count");
        require(peopleCount.isIntegralNumber() && peopleCount.canConvertToInt() && peopleCount.intValue() >= 0,
                "VLM people_count must be a nonnegative integer");
        validateNestedKeywords(result.get("korean_search_keywords"));
        require(validText(result, "detailed_description_ko", MAX_DOCUMENT_LENGTH),
                "VLM detailed_description_ko is invalid");
        require(result.path("frame_count").isIntegralNumber() && result.path("frame_count").intValue() == 8,
                "VLM frame_count must be 8");
        require(result.path("is_mock").isBoolean(), "VLM is_mock must be boolean");
        boolean resultMock = result.path("is_mock").booleanValue();
        String provider = requiredText(result, "provider");
        require(resultMock == expected.mock(), "VLM mock mode mismatch");
        require(expected.mock() ? "mock".equals(provider) : "gemini".equals(provider),
                "VLM provider mismatch");

        VlmIndexPayload.Search search = payload.search();
        require(search != null, "search is required");
        require(search.document() != null && !search.document().isBlank()
                        && search.document().length() <= MAX_DOCUMENT_LENGTH,
                "Search document is blank or too long");
        require(search.embeddingModel() != null && !search.embeddingModel().isBlank()
                        && search.embeddingModel().length() <= 80,
                "Embedding model is invalid");
        require(expected.mock()
                        ? search.embeddingModel().startsWith("mock-")
                        : search.embeddingModel().startsWith("gemini-"),
                "Embedding model does not match VLM provider mode");
        require(search.embeddingDimension() == EMBEDDING_DIMENSION, "Embedding dimension must be 768");
        require(search.embedding() != null && search.embedding().size() == EMBEDDING_DIMENSION,
                "Embedding must contain exactly 768 values");
        for (Double value : search.embedding()) {
            require(value != null && Double.isFinite(value), "Embedding values must be finite");
        }
        validateKeywords(search.keywords());
        return payload;
    }

    private void validateKeywords(List<String> keywords) {
        require(keywords != null && !keywords.isEmpty(), "Search keywords are required");
        Set<String> unique = new HashSet<>();
        for (String keyword : keywords) {
            require(keyword != null && !keyword.isBlank() && keyword.length() <= MAX_KEYWORD_LENGTH,
                    "Search keyword is invalid");
            require(unique.add(keyword), "Search keywords must be unique");
        }
    }

    private void validateNestedKeywords(JsonNode keywords) {
        require(keywords != null && keywords.isArray() && !keywords.isEmpty(),
                "VLM korean_search_keywords must be a nonempty array");
        Set<String> unique = new HashSet<>();
        for (JsonNode keyword : keywords) {
            require(keyword.isTextual() && !keyword.textValue().isBlank()
                            && keyword.textValue().length() <= MAX_KEYWORD_LENGTH,
                    "VLM korean_search_keyword is invalid");
            require(unique.add(keyword.textValue()), "VLM korean_search_keywords must be unique");
        }
    }

    private boolean validText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                && value.textValue().length() <= maxLength;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        require(value != null && value.isTextual() && !value.textValue().isBlank(), field + " is required");
        return value.textValue();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public record Expected(String incidentId, String cameraLoginId, OffsetDateTime capturedAt, boolean mock) {
    }
}
