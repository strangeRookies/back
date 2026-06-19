package com.strange.safety.alert.cache;

import com.strange.safety.alert.dto.AlertEventResponse;
import java.util.List;

public interface RecentAlertCacheStore {

    void add(Long facilityId, AlertEventResponse alert);

    List<AlertEventResponse> findRecent(Long facilityId);
}
