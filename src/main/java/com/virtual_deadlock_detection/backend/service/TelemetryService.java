package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.dto.TelemetryRequest;
import com.virtual_deadlock_detection.backend.dto.TelemetryResponse;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryService {

    private final ThreadService threadService;
    private final LockService lockService;
    private final WaitEdgeService waitEdgeService;

    public TelemetryService(
            ThreadService threadService,
            LockService lockService,
            WaitEdgeService waitEdgeService) {

        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
    }

    @Transactional
    public TelemetryResponse processTelemetry(
            TelemetryRequest request) {

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

        String eventType = request.getEventType() == null
                ? ""
                : request.getEventType().trim().toUpperCase();

        /*
         * ---------------------------------------------------------
         * THREAD
         * ---------------------------------------------------------
         */

        String instanceId = request.getInstanceId() != null
                && !request.getInstanceId().isBlank()
                ? request.getInstanceId()
                : "unknown";

        ThreadEntity thread = new ThreadEntity();

        thread.setThreadId(request.getThreadId());
        thread.setAppName(request.getAppName());
        thread.setInstanceId(instanceId);
        thread.setThreadName(request.getThreadName());
        thread.setState(request.getState());
        thread.setCarrierThread(request.getCarrierThread());

        thread = threadService.registerOrUpdateThread(thread);

        /*
         * ---------------------------------------------------------
         * LOCK
         * ---------------------------------------------------------
         *
         * Only process lock information when the event actually
         * contains a lock ID.
         */

        LockEntity lock = null;

        if (request.getLockId() != null
                && !request.getLockId().isBlank()) {

            lock = new LockEntity();

            lock.setLockId(request.getLockId());
            lock.setLockType(
                    request.getLockType() == null
                            ? "UNKNOWN"
                            : request.getLockType()
            );

            // Problem 1 fix: scope lock identity to the sending JVM instance.
            // The lockId is a JVM memory address that is NOT globally unique;
            // the composite (lockId + instanceId) is.
            lock.setInstanceId(instanceId);

            /*
             * For LOCK_ACQUIRE events, the event's thread is the new owner.
             */
            if ("LOCK_ACQUIRE".equals(eventType)) {
                lock.setOwnerThread(thread);
            }

            /*
             * For THREAD_BLOCKED or LOCK_OWNED events, ownerThreadId tells us who owns
             * the lock. Find or register that owner thread within the same instance scope.
             */
            if (("THREAD_BLOCKED".equals(eventType) || "LOCK_OWNED".equals(eventType))
                    && request.getOwnerThreadId() != null
                    && request.getOwnerThreadId() != 0
                    && !request.getOwnerThreadId().equals(request.getThreadId())) {

                ThreadEntity owner =
                        threadService.getThreadByThreadIdAndAppNameAndInstanceId(
                                request.getOwnerThreadId(),
                                request.getAppName(),
                                instanceId
                        ).orElseGet(() -> {
                            ThreadEntity newOwner = new ThreadEntity();
                            newOwner.setThreadId(request.getOwnerThreadId());
                            newOwner.setAppName(request.getAppName());
                            newOwner.setInstanceId(instanceId);
                            newOwner.setThreadName(
                                    request.getOwnerThreadName() != null && !request.getOwnerThreadName().isBlank()
                                            ? request.getOwnerThreadName()
                                            : "thread-" + request.getOwnerThreadId()
                            );
                            newOwner.setState(ThreadState.RUNNABLE);
                            return threadService.registerOrUpdateThread(newOwner);
                        });

                lock.setOwnerThread(owner);
            }

            lock = lockService.registerOrUpdateLock(lock);
        }

        /*
         * ---------------------------------------------------------
         * WAIT-EDGE LIFECYCLE
         * ---------------------------------------------------------
         */

        switch (eventType) {

            /*
             * Thread is waiting for a lock.
             *
             * Thread -> Lock
             */
            case "THREAD_BLOCKED":
            case "LOCK_OWNED":
            case "THREAD_PARKED":

                if (lock != null) {
                    waitEdgeService.createWaitEdge(
                            thread,
                            lock
                    );
                }

                break;

            /*
             * Thread successfully acquired the lock.
             *
             * Therefore the previous wait relationship is gone.
             */
            case "LOCK_ACQUIRE":

                if (lock != null) {
                    waitEdgeService.removeEdgesForLock(lock);
                }

                break;

            /*
             * Lock released.
             *
             * Remove wait relationships associated with it.
             */
            case "LOCK_RELEASE":

                if (lock != null) {
                    waitEdgeService.removeEdgesForLock(lock);
                }

                break;

            /*
             * Thread ended.
             *
             * Remove all edges where this thread was waiting.
             */
            case "THREAD_END":

                waitEdgeService.removeEdgesForThread(thread);

                break;

            /*
             * Other lifecycle events
             */
            case "THREAD_START":
            case "PINNING":
            default:
                break;
        }

        return new TelemetryResponse(
                true,
                "Telemetry stored successfully"
        );
    }
}