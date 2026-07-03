package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitEdgeRepository extends JpaRepository<WaitEdgeEntity, Long> {

    List<WaitEdgeEntity> findByWaitingThread(ThreadEntity thread);

    List<WaitEdgeEntity> findByLock(LockEntity lock);

    Optional<WaitEdgeEntity> findByWaitingThreadAndLock(
            ThreadEntity thread,
            LockEntity lock
    );
}