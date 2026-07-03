package com.virtual_deadlock_detection.backend.mapper;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import org.springframework.stereotype.Component;

@Component
public class GraphEntityMapper {

    private final ThreadRepository threadRepository;
    private final LockRepository lockRepository;

    public GraphEntityMapper(ThreadRepository threadRepository,
                             LockRepository lockRepository) {

        this.threadRepository = threadRepository;
        this.lockRepository = lockRepository;
    }

    public ThreadEntity getThread(GraphNode node) {

        if (node.getType() != NodeType.VIRTUAL_THREAD &&
                node.getType() != NodeType.CARRIER_THREAD) {
            return null;
        }

        return threadRepository
                .findByThreadId(node.getId())
                .orElse(null);
    }

    public LockEntity getLock(GraphNode node) {

        if (node.getType() != NodeType.LOCK) {
            return null;
        }

        return lockRepository
                .findByLockId(String.valueOf(node.getId()))
                .orElse(null);
    }
}