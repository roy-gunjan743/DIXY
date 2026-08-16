package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThreadRepository extends JpaRepository<ThreadEntity, Long> {

    /**
     * Fully-scoped lookup -- canonical method for ingestion & correlation.
     * Prevents cross-instance and cross-app thread collisions.
     */
    Optional<ThreadEntity> findByThreadIdAndAppNameAndInstanceId(
            Long threadId,
            String appName,
            String instanceId
    );

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT INTO threads (thread_id, app_name, instance_id, thread_name, state, carrier_thread, created_at) " +
            "VALUES (:threadId, :appName, :instanceId, :threadName, :state, :carrierThread, NOW()) " +
            "ON CONFLICT (thread_id, app_name, instance_id) DO UPDATE SET " +
            "thread_name = EXCLUDED.thread_name, " +
            "state = EXCLUDED.state, " +
            "carrier_thread = COALESCE(EXCLUDED.carrier_thread, threads.carrier_thread)", nativeQuery = true)
    void upsertThread(
            @org.springframework.data.repository.query.Param("threadId") Long threadId,
            @org.springframework.data.repository.query.Param("appName") String appName,
            @org.springframework.data.repository.query.Param("instanceId") String instanceId,
            @org.springframework.data.repository.query.Param("threadName") String threadName,
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("carrierThread") String carrierThread
    );

    /**
     * Instance-scoped lookup.
     */
    Optional<ThreadEntity> findByThreadIdAndInstanceId(
            Long threadId,
            String instanceId
    );

    /**
     * @deprecated Use {@link #findByThreadIdAndAppNameAndInstanceId(Long, String, String)} instead.
     */
    @Deprecated
    Optional<ThreadEntity> findByThreadIdAndAppName(
            Long threadId,
            String appName
    );

    /**
     * @deprecated Use instance-scoped lookup instead.
     */
    @Deprecated
    Optional<ThreadEntity> findByThreadId(Long threadId);

    Optional<ThreadEntity> findByThreadName(String threadName);
}