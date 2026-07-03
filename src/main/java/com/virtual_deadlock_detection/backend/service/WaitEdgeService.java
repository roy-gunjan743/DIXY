package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WaitEdgeService {

    private final WaitEdgeRepository waitEdgeRepository;

    public WaitEdgeService(WaitEdgeRepository waitEdgeRepository) {
        this.waitEdgeRepository = waitEdgeRepository;
    }

    public WaitEdgeEntity saveWaitEdge(WaitEdgeEntity waitEdge) {
        return waitEdgeRepository.save(waitEdge);
    }

    public List<WaitEdgeEntity> getAllWaitEdges() {
        return waitEdgeRepository.findAll();
    }

    public Optional<WaitEdgeEntity> getWaitEdge(Long id) {
        return waitEdgeRepository.findById(id);
    }

    public void deleteWaitEdge(Long id) {
        waitEdgeRepository.deleteById(id);
    }
}