package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.MonitoringSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MonitoringSessionRepository extends JpaRepository<MonitoringSessionEntity, Long> {
    Optional<MonitoringSessionEntity> findFirstByInstance_IdAndEndedAtIsNullOrderByStartedAtDesc(Long instanceId);
}
