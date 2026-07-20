package com.strange.safety.incident;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(originPatterns = {"http://localhost:3000", "http://localhost:5173"})
public class IncidentAcknowledgeController {

    private final IncidentAcknowledgeService service;

    public IncidentAcknowledgeController(IncidentAcknowledgeService service) {
        this.service = service;
    }

    @PostMapping("/{eventId}/acknowledge-and-record")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentRecordingRecord acknowledgeAndRecord(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.strange.safety.auth.security.CustomUserDetails userDetails,
            @PathVariable String eventId,
            @Valid @RequestBody AcknowledgeIncidentRequest request
    ) {
        return service.acknowledgeAndRequestRecording(userDetails.getUserId(), eventId, request);
    }
}
