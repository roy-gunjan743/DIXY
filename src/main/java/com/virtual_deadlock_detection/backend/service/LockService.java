package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
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

    public LockEntity saveLock(LockEntity lock) {
        return lockRepository.save(lock);
    }

    public List<LockEntity> getAllLocks() {
        return lockRepository.findAll();
    }

    public Optional<LockEntity> getLockByLockId(String lockId) {
        return lockRepository.findByLockId(lockId);
    }

    public void deleteLock(Long id) {
        lockRepository.deleteById(id);
    }

    /**
     * Registers a lock if it does not exist.
     * Updates the existing lock if it already exists.
     *
     * synchronized prevents two telemetry requests in this
     * VDD instance from inserting the same lock simultaneously.
     */
    public synchronized LockEntity registerOrUpdateLock(LockEntity lock) {

        if (lock == null) {
            throw new IllegalArgumentException(
                    "Lock cannot be null"
            );
        }

        if (lock.getLockId() == null || lock.getLockId().isBlank()) {
            throw new IllegalArgumentException(
                    "Lock ID cannot be null or blank"
            );
        }

        Optional<LockEntity> existing =
                lockRepository.findByLockId(lock.getLockId());

        if (existing.isPresent()) {

            LockEntity current = existing.get();

            if (lock.getLockType() != null) {
                current.setLockType(lock.getLockType());
            }

            if (lock.getOwnerThread() != null) {
                current.setOwnerThread(lock.getOwnerThread());
            }

            return lockRepository.save(current);
        }

        return lockRepository.save(lock);
    }
}