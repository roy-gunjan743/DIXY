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

    public TelemetryService(
            ThreadService threadService,
            LockService lockService) {

        this.threadService = threadService;
        this.lockService = lockService;
    }

    public TelemetryResponse processTelemetry(
            TelemetryRequest request) {

        /*
         * Basic validation
         */
        if (request == null) {
            throw new IllegalArgumentException(
                    "Telemetry request cannot be null"
            );
        }

        if (request.getThreadId() == null) {
            throw new IllegalArgumentException(
                    "Telemetry request requires threadId"
            );
        }

        /*
         * Build thread entity
         */
        ThreadEntity thread = new ThreadEntity();

        thread.setThreadId(request.getThreadId());
        thread.setAppName(request.getAppName());
        thread.setThreadName(request.getThreadName());
        thread.setState(request.getState());
        thread.setCarrierThread(request.getCarrierThread());

        /*
         * registerOrUpdateThread() handles:
         *
         * - missing state
         * - existing thread
         * - appName separation
         * - null state updates
         */
        thread = threadService.registerOrUpdateThread(thread);

        /*
         * Only create a lock when the telemetry event
         * actually contains lock information.
         */
        if (request.getLockId() != null
                && !request.getLockId().isBlank()) {

            LockEntity lock = new LockEntity();

            lock.setLockId(request.getLockId());
            lock.setLockType(request.getLockType());
            lock.setOwnerThread(thread);

            lockService.registerOrUpdateLock(lock);
        }

        return new TelemetryResponse(
                true,
                "Telemetry stored successfully"
        );
    }
}