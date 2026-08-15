package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WaitEdgeService {

    private final WaitEdgeRepository waitEdgeRepository;

    public WaitEdgeService(WaitEdgeRepository waitEdgeRepository) {
        this.waitEdgeRepository = waitEdgeRepository;
    }

    /**
     * Creates a waiting-thread -> lock edge if it doesn't already exist.
     *
     * synchronized prevents concurrent telemetry requests from creating
     * the same edge twice inside this VDD instance.
     */
    public synchronized WaitEdgeEntity createWaitEdge(
            ThreadEntity thread,
            LockEntity lock) {

        if (thread == null) {
            throw new IllegalArgumentException(
                    "Waiting thread cannot be null"
            );
        }

        if (lock == null) {
            throw new IllegalArgumentException(
                    "Lock cannot be null"
            );
        }

        return waitEdgeRepository
                .findByWaitingThreadAndLock(thread, lock)
                .orElseGet(() -> {

                    WaitEdgeEntity edge = new WaitEdgeEntity();

                    edge.setWaitingThread(thread);
                    edge.setLock(lock);

                    return waitEdgeRepository.save(edge);
                });
    }

    @Transactional(readOnly = true)
    public List<WaitEdgeEntity> getAllEdges() {
        return waitEdgeRepository.findAllWithThreadsAndLocks();
    }

    public void deleteEdge(Long id) {
        waitEdgeRepository.deleteById(id);
    }
}