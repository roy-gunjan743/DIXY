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
    private final WaitEdgeService waitEdgeService;

    public TelemetryService(
            ThreadService threadService,
            LockService lockService,
            WaitEdgeService waitEdgeService) {

        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
    }

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

        ThreadEntity thread = new ThreadEntity();

        thread.setThreadId(request.getThreadId());
        thread.setAppName(request.getAppName());
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

            /*
             * For LOCK_OWNED / LOCK_ACQUIRE events, the event's
             * thread is the owner.
             */
            if ("LOCK_OWNED".equals(eventType)
                    || "LOCK_ACQUIRE".equals(eventType)) {

                lock.setOwnerThread(thread);
            }

            /*
             * For a BLOCKED event, ownerThreadId tells us who owns
             * the lock. Find that thread if it exists.
             */
            if ("THREAD_BLOCKED".equals(eventType)
                    && request.getOwnerThreadId() != null) {

                ThreadEntity owner =
                        threadService.getThreadByThreadId(
                                request.getOwnerThreadId()
                        ).orElse(null);

                if (owner != null) {
                    lock.setOwnerThread(owner);
                }
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

                if (lock != null) {
                    waitEdgeService.createWaitEdge(
                            thread,
                            lock
                    );
                }

                break;

            /*
             * A parked thread can represent a waiting relationship
             * when lock information is supplied by the agent.
             */
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
             * Ownership information by itself does not create
             * a waiting relationship.
             */
            case "LOCK_OWNED":
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