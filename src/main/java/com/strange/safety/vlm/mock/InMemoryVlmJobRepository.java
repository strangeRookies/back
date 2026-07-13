package com.strange.safety.vlm.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryVlmJobRepository implements VlmJobRepository {
    private final Map<String, VlmJob> jobsById = new LinkedHashMap<>();

    @Override
    public VlmJob save(VlmJob job) {
        jobsById.put(job.jobId(), job);
        return job;
    }

    @Override
    public List<VlmJob> findAll() {
        return new ArrayList<>(jobsById.values());
    }
}
