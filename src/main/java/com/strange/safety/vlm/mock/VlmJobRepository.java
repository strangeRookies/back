package com.strange.safety.vlm.mock;

import java.util.List;

public interface VlmJobRepository {
    VlmJob save(VlmJob job);

    List<VlmJob> findAll();
}
