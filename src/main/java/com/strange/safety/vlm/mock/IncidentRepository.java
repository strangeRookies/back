package com.strange.safety.vlm.mock;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository {
    Incident save(Incident incident);

    Optional<Incident> findByCameraLoginIdAndOriginalEventId(String cameraLoginId, String originalEventId);

    Optional<Incident> findById(String incidentId);

    List<Incident> findAll();
}
