package com.strange.safety.incident;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class IncidentAcknowledgeService {

    private final IncidentRecordingRepository repository;
    private final com.strange.safety.alert.service.AlertEventService alertEventService;
    private final Clock clock;

    @Autowired
    public IncidentAcknowledgeService(IncidentRecordingRepository repository, com.strange.safety.alert.service.AlertEventService alertEventService) {
        this(repository, alertEventService, Clock.systemUTC());
    }

    IncidentAcknowledgeService(IncidentRecordingRepository repository, com.strange.safety.alert.service.AlertEventService alertEventService, Clock clock) {
        this.repository = repository;
        this.alertEventService = alertEventService;
        this.clock = clock;
    }

    public IncidentRecordingRecord acknowledgeAndRequestRecording(
            Long userId,
            String pathEventId,
            AcknowledgeIncidentRequest request
    ) {
        String eventId = request.eventId().isBlank() ? pathEventId : request.eventId();
        IncidentRecordingRecord record = new IncidentRecordingRecord(
                eventId,
                request.cameraId(),
                request.eventType(),
                request.eventTimestamp(),
                Instant.now(clock),
                request.acknowledgedBy(),
                request.preFrames(),
                request.postFrames(),
                request.totalFrames(),
                RecordingStatus.RECORDING_REQUESTED
        );
        
        IncidentRecordingRecord saved = repository.save(record);
        
        try {
            alertEventService.acknowledgeByEventId(userId, eventId);
        } catch (Exception e) {
            // log and ignore if not found
        }
        
        return saved;
    }
}
