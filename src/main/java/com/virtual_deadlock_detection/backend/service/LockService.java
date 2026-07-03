package com.virtual_deadlock_detection.backend.service;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LockService {
    private final ThreadRepository threadRepository;
    private final LockRepository lockRepository;

    public LockService(LockRepository lockRepository,
                       ThreadRepository threadRepository){
        this.lockRepository=lockRepository;
        this.threadRepository=threadRepository;
    }
    public LockEntity saveLock(LockEntity lock){
        return lockRepository.save(lock);
    }

    public Optional<LockEntity>getLockByLockId(String lockId){
        return lockRepository.findByLockId(lockId);
    }

    public void deleteLock(Long id){
        lockRepository.deleteById(id);
    }

}
