package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.dto.TelemetryRequest;
import com.virtual_deadlock_detection.backend.dto.TelemetryResponse;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {

    private final ThreadService threadService;
    private final LockService lockService;

    public TelemetryService(ThreadService threadService,
                            LockService lockService) {

        this.threadService = threadService;
        this.lockService = lockService;
    }

    public TelemetryResponse processTelemetry(TelemetryRequest request) {

        ThreadEntity thread = new ThreadEntity();

        thread.setThreadId(request.getThreadId());
        thread.setThreadName(request.getThreadName());
        thread.setState(request.getState());
        thread.setCarrierThread(request.getCarrierThread());

        thread = threadService.registerOrUpdateThread(thread);

        LockEntity lock = new LockEntity();

        lock.setLockId(request.getLockId());
        lock.setLockType(request.getLockType());
        lock.setOwnerThread(thread);

        lockService.registerOrUpdateLock(lock);

        return new TelemetryResponse(
                true,
                "Telemetry stored successfully"
        );
    }
}