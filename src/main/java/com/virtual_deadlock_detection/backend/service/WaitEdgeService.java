package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WaitEdgeService {

    private final WaitEdgeRepository waitEdgeRepository;

    public WaitEdgeService(WaitEdgeRepository waitEdgeRepository) {
        this.waitEdgeRepository = waitEdgeRepository;
    }

    public WaitEdgeEntity createWaitEdge(
            ThreadEntity thread,
            LockEntity lock) {

        return waitEdgeRepository
                .findByWaitingThreadAndLock(thread, lock)
                .orElseGet(() -> {

                    WaitEdgeEntity edge = new WaitEdgeEntity();

                    edge.setWaitingThread(thread);
                    edge.setLock(lock);

                    return waitEdgeRepository.save(edge);

                });
    }

    public List<WaitEdgeEntity> getAllEdges() {
        return waitEdgeRepository.findAll();
    }

    public void deleteEdge(Long id) {
        waitEdgeRepository.deleteById(id);
    }
}