package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LockService {

    private final LockRepository lockRepository;
    private final ThreadRepository threadRepository;

    public LockService(LockRepository lockRepository,
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
    public LockEntity registerOrUpdateLock(LockEntity lock) {

        return lockRepository.findByLockId(lock.getLockId())
                .map(existing -> {

                    existing.setLockType(lock.getLockType());
                    existing.setOwnerThread(lock.getOwnerThread());

                    return lockRepository.save(existing);

                })
                .orElseGet(() -> lockRepository.save(lock));
    }
}