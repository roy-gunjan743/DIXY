package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface WaitEdgeRepository extends JpaRepository<WaitEdgeEntity,Long> {
}
