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
     * Create a wait edge only when the same
     * thread -> lock relationship does not already exist.
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

    /**
     * Remove every wait edge associated with a lock.
     */
    @Transactional
    public void removeEdgesForLock(LockEntity lock) {

        if (lock == null) {
            return;
        }

        List<WaitEdgeEntity> edges =
                waitEdgeRepository.findByLock(lock);

        if (!edges.isEmpty()) {
            waitEdgeRepository.deleteAll(edges);
        }
    }

    /**
     * Remove every wait edge originating from a thread.
     */
    @Transactional
    public void removeEdgesForThread(ThreadEntity thread) {

        if (thread == null) {
            return;
        }

        List<WaitEdgeEntity> edges =
                waitEdgeRepository.findByWaitingThread(thread);

        if (!edges.isEmpty()) {
            waitEdgeRepository.deleteAll(edges);
        }
    }

    /**
     * Used by the CLI and graph builder.
     */
    @Transactional(readOnly = true)
    public List<WaitEdgeEntity> getAllEdges() {
        return waitEdgeRepository.findAllWithThreadsAndLocks();
    }

    public void deleteEdge(Long id) {
        waitEdgeRepository.deleteById(id);
    }
}