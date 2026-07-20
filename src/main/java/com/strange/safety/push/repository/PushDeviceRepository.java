package com.strange.safety.push.repository;

import com.strange.safety.push.entity.PushDevice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByToken(String token);

    Optional<PushDevice> findByTokenAndUser_Id(String token, Long userId);

    List<PushDevice> findByUser_IdAndActiveTrue(Long userId);

    List<PushDevice> findByUser_IdInAndActiveTrue(Collection<Long> userIds);

    List<PushDevice> findByUser_Id(Long userId);
}
