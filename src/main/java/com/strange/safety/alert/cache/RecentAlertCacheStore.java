package com.strange.safety.alert.cache;

import com.strange.safety.alert.dto.AlertEventResponse;
import java.util.List;

public interface RecentAlertCacheStore {

    void add(String contextKey, AlertEventResponse alert);

    List<AlertEventResponse> findRecent(String contextKey);
}
