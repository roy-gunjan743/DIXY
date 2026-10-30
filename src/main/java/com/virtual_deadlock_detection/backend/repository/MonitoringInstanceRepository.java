package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.MonitoringInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MonitoringInstanceRepository extends JpaRepository<MonitoringInstanceEntity, Long> {
    Optional<MonitoringInstanceEntity> findByInstanceId(String instanceId);
}
