package com.strange.safety.alert.repository;

import com.strange.safety.alert.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    List<Snapshot> findByAlertEvent_Id(Long alertEventId);

    boolean existsByAlertEvent_IdAndSnapshotUrl(Long alertEventId, String snapshotUrl);
}
