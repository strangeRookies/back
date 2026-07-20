package com.strange.safety.incident;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "mqtt.initial-connect-delay-ms=3600000")
@WithMockUser
class IncidentAcknowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acknowledgeAndRecordReturnsSavedRecordingRequest() throws Exception {
        String payload = """
                {
                  "eventId": "camera-1:Faint:1780550000:7",
                  "cameraId": "camera-1",
                  "eventType": "Faint",
                  "eventTimestamp": "2026-06-04T03:00:00Z",
                  "preFrames": 150,
                  "postFrames": 150,
                  "totalFrames": 300,
                  "status": "acknowledged",
                  "reason": "acknowledged",
                  "acknowledgedBy": "safety-user"
                }
                """;

        mockMvc.perform(post("/api/incidents/{eventId}/acknowledge-and-record", "camera-1:Faint:1780550000:7")
                        .with(user(new CustomUserDetails(1L, "safety-user@example.com", Role.INDIVIDUAL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", is("camera-1:Faint:1780550000:7")))
                .andExpect(jsonPath("$.cameraId", is("camera-1")))
                .andExpect(jsonPath("$.eventType", is("Faint")))
                .andExpect(jsonPath("$.preFrames", is(150)))
                .andExpect(jsonPath("$.postFrames", is(150)))
                .andExpect(jsonPath("$.totalFrames", is(300)))
                .andExpect(jsonPath("$.recordingStatus", is("RECORDING_REQUESTED")))
                .andExpect(jsonPath("$.acknowledgedBy", is("safety-user")));
    }

    @Test
    void acknowledgeAndRecordRejectsMissingCameraId() throws Exception {
        String payload = """
                {
                  "eventId": "event-1",
                  "eventType": "Faint",
                  "eventTimestamp": "2026-06-04T03:00:00Z",
                  "preFrames": 150,
                  "postFrames": 150,
                  "totalFrames": 300,
                  "status": "acknowledged",
                  "reason": "acknowledged"
                }
                """;

        mockMvc.perform(post("/api/incidents/{eventId}/acknowledge-and-record", "event-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgeAndRecordRejectsMissingEventId() throws Exception {
        String payload = """
                {
                  "cameraId": "camera-1",
                  "eventType": "Faint",
                  "eventTimestamp": "2026-06-04T03:00:00Z",
                  "preFrames": 150,
                  "postFrames": 150,
                  "totalFrames": 300,
                  "status": "acknowledged",
                  "reason": "acknowledged"
                }
                """;

        mockMvc.perform(post("/api/incidents/{eventId}/acknowledge-and-record", "event-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
