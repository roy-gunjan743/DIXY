package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.service.LockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locks")
public class LockController {

    private final LockService lockService;

    public LockController(LockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping
    public LockEntity createLock(@RequestBody LockEntity lock) {
        return lockService.saveLock(lock);
    }

    @GetMapping
    public List<LockEntity> getAllLocks() {
        return lockService.getAllLocks();
    }

    @GetMapping("/{lockId}")
    public LockEntity getLock(@PathVariable String lockId) {
        return lockService.getLockByLockId(lockId)
                .orElseThrow(() -> new RuntimeException("Lock not found"));
    }

    @DeleteMapping("/{id}")
    public void deleteLock(@PathVariable Long id) {
        lockService.deleteLock(id);
    }
}