package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadlockReportRepository extends JpaRepository<DeadlockReportEntity, Long> {

    @Modifying
    @Query(
            value = """
                    TRUNCATE TABLE
                        deadlock_nodes,
                        deadlock_reports,
                        wait_edges,
                        locks,
                        threads
                    RESTART IDENTITY CASCADE
                    """,
            nativeQuery = true
    )
    void truncateAllTelemetry();
}