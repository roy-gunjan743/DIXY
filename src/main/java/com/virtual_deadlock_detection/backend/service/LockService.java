package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LockService {

    private final LockRepository lockRepository;
    private final ThreadRepository threadRepository;

    public LockService(
            LockRepository lockRepository,
            ThreadRepository threadRepository) {

        this.lockRepository = lockRepository;
        this.threadRepository = threadRepository;
    }

    @Transactional
    public void clearOwnerIfOwnedBy(LockEntity lock, ThreadEntity releasingThread) {
        if (lock == null || releasingThread == null || releasingThread.getId() == null) return;
        lockRepository.clearOwnerIfMatches(
                lock.getLockId(), lock.getInstanceId(), releasingThread.getId());
    }

    @Transactional
    public void clearOwnersForThread(ThreadEntity thread) {
        if (thread == null || thread.getId() == null) return;
        lockRepository.clearOwnersForThread(thread.getId());
    }

    public LockEntity saveLock(LockEntity lock) {
        return lockRepository.save(lock);
    }

    public List<LockEntity> getAllLocks() {
        return lockRepository.findAll();
    }

    /** @deprecated Use instance-scoped lookup where possible. */
    @Deprecated
    public Optional<LockEntity> getLockByLockId(String lockId) {
        return lockRepository.findByLockId(lockId);
    }

    public void deleteLock(Long id) {
        lockRepository.deleteById(id);
    }

    /**
     * Registers a lock if it does not exist; updates the existing record if it does.
     *
     * Lock identity is now composite: (lockId + instanceId).
     * A lock_id value is a JVM memory address that is NOT globally unique —
     * the same numeric address can appear in different JVM processes.
    /**
     * Registers a lock if it does not exist; updates the existing record if it does.
     *
     * Composite identity: (lockId + instanceId).
     * Database-level uniqueness (uq_locks_lock_id_instance_id) is the authoritative
     * guard across multiple backend pods and concurrent threads.
     */
    @Transactional
    public LockEntity registerOrUpdateLock(LockEntity lock) {

        if (lock == null) {
            throw new IllegalArgumentException("Lock cannot be null");
        }

        if (lock.getLockId() == null || lock.getLockId().isBlank()) {
            throw new IllegalArgumentException("Lock ID cannot be null or blank");
        }

        String instanceId = lock.getInstanceId() == null || lock.getInstanceId().isBlank()
                ? "unknown" : lock.getInstanceId();
        String lockType = lock.getLockType() == null ? "UNKNOWN" : lock.getLockType();

        lockRepository.upsertLock(lock.getLockId(), instanceId, lockType);

        LockEntity saved = lockRepository.findByLockIdAndInstanceId(lock.getLockId(), instanceId)
                .orElse(lock);

        if (lock.getOwnerThread() != null) {
            saved.setOwnerThread(lock.getOwnerThread());
            return lockRepository.save(saved);
        }

        return saved;
    }
}
