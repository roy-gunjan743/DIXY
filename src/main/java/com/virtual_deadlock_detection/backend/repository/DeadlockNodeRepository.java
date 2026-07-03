package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.DeadlockNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadlockNodeRepository extends JpaRepository<DeadlockNodeEntity, Long> {

}