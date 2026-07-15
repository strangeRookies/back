package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VlmIndexPayloadParserTest {
    private static final String INCIDENT_ID = "incident-123";
    private static final String CAMERA_ID = "camera-7";
    private static final OffsetDateTime CAPTURED_AT = OffsetDateTime.parse("2026-07-15T12:30:00+09:00");

    private ObjectMapper mapper;
    private VlmIndexPayloadParser parser;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().findAndRegisterModules();
        parser = new VlmIndexPayloadParser(mapper);
    }

    @Test
    void acceptsStrictValidPayload() throws Exception {
        assertDoesNotThrow(() -> parser.parseAndValidate(json(validPayload()), expected(false)));
    }

    @Test
    void rejectsWrongEmbeddingLengthsAndNonFiniteValues() throws Exception {
        ObjectNode shortPayload = validPayload();
        shortPayload.withObject("/search").withArray("embedding").remove(767);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(shortPayload), expected(false)));

        ObjectNode longPayload = validPayload();
        longPayload.withObject("/search").withArray("embedding").add(0.0d);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(longPayload), expected(false)));

        ObjectNode nanPayload = validPayload();
        nanPayload.withObject("/search").withArray("embedding").set(0, mapper.getNodeFactory().numberNode(Double.NaN));
        assertThrows(Exception.class, () -> parser.parseAndValidate(json(nanPayload), expected(false)));

        ObjectNode infinityPayload = validPayload();
        infinityPayload.withObject("/search").withArray("embedding")
                .set(0, mapper.getNodeFactory().numberNode(Double.POSITIVE_INFINITY));
        assertThrows(Exception.class, () -> parser.parseAndValidate(json(infinityPayload), expected(false)));
    }

    @Test
    void rejectsEnvelopeVlmCameraAndProviderMismatches() throws Exception {
        ObjectNode envelopeMismatch = validPayload();
        envelopeMismatch.put("incident_id", "other");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(envelopeMismatch), expected(false)));

        ObjectNode vlmMismatch = validPayload();
        vlmMismatch.withObject("/vlm_result").put("incident_id", "other");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(vlmMismatch), expected(false)));

        ObjectNode cameraMismatch = validPayload();
        cameraMismatch.withObject("/vlm_result").put("camera_login_id", "other");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(cameraMismatch), expected(false)));

        ObjectNode providerMismatch = validPayload();
        providerMismatch.withObject("/vlm_result").put("provider", "mock").put("is_mock", true);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(providerMismatch), expected(false)));
    }

    @Test
    void rejectsSchemaUnknownFieldsFrameCountTimestampAndDuplicateKeywords() throws Exception {
        ObjectNode wrongSchema = validPayload();
        wrongSchema.put("schema_version", "v2");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(wrongSchema), expected(false)));

        ObjectNode unknownField = validPayload();
        unknownField.put("unexpected", true);
        assertThrows(Exception.class,
                () -> parser.parseAndValidate(json(unknownField), expected(false)));

        ObjectNode wrongFrames = validPayload();
        wrongFrames.withObject("/vlm_result").put("frame_count", 7);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(wrongFrames), expected(false)));

        ObjectNode wrongTimestamp = validPayload();
        wrongTimestamp.put("captured_at", "2026-07-15T12:31:00+09:00");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(wrongTimestamp), expected(false)));

        ObjectNode duplicateKeywords = validPayload();
        duplicateKeywords.withObject("/search").withArray("keywords").add("fall");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseAndValidate(json(duplicateKeywords), expected(false)));
    }

    private ObjectNode validPayload() {
        ObjectNode root = mapper.createObjectNode();
        root.put("schema_version", VlmIndexPayloadParser.SCHEMA_VERSION);
        root.put("incident_id", INCIDENT_ID);
        root.put("camera_login_id", CAMERA_ID);
        root.put("captured_at", CAPTURED_AT.toString());
        root.putObject("vlm_result")
                .put("incident_id", INCIDENT_ID)
                .put("camera_login_id", CAMERA_ID)
                .put("frame_count", 8)
                .put("provider", "gemini")
                .put("is_mock", false)
                .put("detail", "full provider result is preserved");
        ObjectNode search = root.putObject("search");
        search.put("document", "A worker fell in a monitored corridor");
        search.putArray("keywords").add("fall").add("worker");
        search.put("embedding_model", "text-embedding-model");
        search.put("embedding_dimension", 768);
        ArrayNode embedding = search.putArray("embedding");
        for (int index = 0; index < 768; index += 1) {
            embedding.add(index == 0 ? 1.0d : 0.0d);
        }
        return root;
    }

    private String json(ObjectNode payload) throws Exception {
        return mapper.writeValueAsString(payload);
    }

    private VlmIndexPayloadParser.Expected expected(boolean mock) {
        return new VlmIndexPayloadParser.Expected(INCIDENT_ID, CAMERA_ID, CAPTURED_AT, mock);
    }
}
