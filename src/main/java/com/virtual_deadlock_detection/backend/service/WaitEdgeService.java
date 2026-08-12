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

    /**
     * Loads all wait-edges with waitingThread / lock / lock.ownerThread
     * eagerly fetched (see WaitEdgeRepository.findAllWithThreadsAndLocks()).
     *
     * This app runs in two Spring profiles — "cli" (web-application-type:
     * none, no servlet, no Open-Session-In-View) and "web" (servlet, OSIV
     * on by default). Callers of getAllEdges() (StatusController's web
     * dashboard, DeadlockCommands' CLI "status") both walk the lazy
     * waitingThread/lock/ownerThread associations on the returned entities.
     * That only ever worked for the web caller because OSIV happened to
     * keep a session open for the request; the CLI caller has no such
     * safety net. Fetching eagerly here makes the result safe to walk
     * regardless of which profile/caller is asking.
     */
    @Transactional(readOnly = true)
    public List<WaitEdgeEntity> getAllEdges() {
        return waitEdgeRepository.findAllWithThreadsAndLocks();
    }

    public void deleteEdge(Long id) {
        waitEdgeRepository.deleteById(id);
    }
}