package com.strange.safety.vlm.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<String, Incident> incidentsById = new LinkedHashMap<>();
    private final Map<String, String> incidentIdsByNaturalKey = new LinkedHashMap<>();

    @Override
    public Incident save(Incident incident) {
        String naturalKey = naturalKey(incident.cameraLoginId(), incident.originalEventId());
        String existingId = incidentIdsByNaturalKey.get(naturalKey);
        if (existingId != null) {
            return incidentsById.get(existingId);
        }
        incidentsById.put(incident.incidentId(), incident);
        incidentIdsByNaturalKey.put(naturalKey, incident.incidentId());
        return incident;
    }

    @Override
    public Optional<Incident> findByCameraLoginIdAndOriginalEventId(String cameraLoginId, String originalEventId) {
        String incidentId = incidentIdsByNaturalKey.get(naturalKey(cameraLoginId, originalEventId));
        return incidentId == null ? Optional.empty() : Optional.ofNullable(incidentsById.get(incidentId));
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
        return Optional.ofNullable(incidentsById.get(incidentId));
    }

    @Override
    public List<Incident> findAll() {
        return new ArrayList<>(incidentsById.values());
    }

    private String naturalKey(String cameraLoginId, String originalEventId) {
        return cameraLoginId + "|" + originalEventId;
    }
}
