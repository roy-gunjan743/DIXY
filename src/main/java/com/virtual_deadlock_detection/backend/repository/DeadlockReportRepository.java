package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadlockReportRepository extends JpaRepository<DeadlockReportEntity, Long> {

}