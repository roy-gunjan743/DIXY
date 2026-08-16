package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LockRepository extends JpaRepository<LockEntity, Long> {

    /**
     * Instance-scoped lookup — the correct method for all hot-path callers.
     * lock_id alone is not globally unique across JVM instances.
     */
    Optional<LockEntity> findByLockIdAndInstanceId(String lockId, String instanceId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT INTO locks (lock_id, instance_id, lock_type, created_at) " +
            "VALUES (:lockId, :instanceId, :lockType, NOW()) " +
            "ON CONFLICT (lock_id, instance_id) DO UPDATE SET " +
            "lock_type = EXCLUDED.lock_type", nativeQuery = true)
    void upsertLock(
            @org.springframework.data.repository.query.Param("lockId") String lockId,
            @org.springframework.data.repository.query.Param("instanceId") String instanceId,
            @org.springframework.data.repository.query.Param("lockType") String lockType
    );

    /**
     * Kept for backward compatibility with LockController (management/query path only).
     * Do NOT use this in telemetry processing — it ignores instance scope.
     *
     * @deprecated Use {@link #findByLockIdAndInstanceId(String, String)} instead.
     */
    @Deprecated
    Optional<LockEntity> findByLockId(String lockId);
}