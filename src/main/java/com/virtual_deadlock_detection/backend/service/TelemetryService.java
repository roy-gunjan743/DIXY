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

    public TelemetryService(
            ThreadService threadService,
            LockService lockService) {

        this.threadService = threadService;
        this.lockService = lockService;
    }

    @Transactional
    public TelemetryResponse processTelemetry(
            TelemetryRequest request) {

        if (request == null) {

            return new TelemetryResponse(
                    false,
                    "Telemetry request cannot be null"
            );
        }

        /*
         * --------------------------------------------------
         * THREAD
         * --------------------------------------------------
         */

        ThreadEntity thread =
                new ThreadEntity();

        thread.setThreadId(
                request.getThreadId()
        );

        thread.setThreadName(
                request.getThreadName()
        );

        /*
         * IMPORTANT:
         *
         * The database identifies a thread using:
         *
         *     threadId + appName
         */
        thread.setAppName(
                request.getAppName()
        );

        /*
         * Resolve the state from the incoming event.
         *
         * If the event does not represent a state change,
         * the ThreadService will preserve the existing state.
         */
        ThreadState resolvedState =
                resolveState(request);

        thread.setState(
                resolvedState
        );

        thread.setCarrierThread(
                request.getCarrierThread()
        );

        thread =
                threadService.registerOrUpdateThread(
                        thread
                );

        /*
         * --------------------------------------------------
         * LOCK
         * --------------------------------------------------
         */

        /*
         * Only create a lock record when the telemetry
         * actually contains a lock identifier.
         *
         * This prevents events such as THREAD_START from
         * creating fake locks with null/empty IDs.
         */
        if (request.getLockId() != null &&
                !request.getLockId().isBlank()) {

            LockEntity lock =
                    new LockEntity();

            lock.setLockId(
                    request.getLockId()
            );

            lock.setLockType(
                    request.getLockType()
            );

            lock.setOwnerThread(
                    thread
            );

            lockService.registerOrUpdateLock(
                    lock
            );
        }

        return new TelemetryResponse(
                true,
                "Telemetry stored successfully"
        );
    }

    /**
     * Converts telemetry event types into the current
     * state of the thread.
     *
     * Some events, such as LOCK_OWNED or LOCK_RELEASE,
     * do not themselves describe a thread state.
     * Those return null so ThreadService can preserve
     * the previously known state.
     */
    private ThreadState resolveState(
            TelemetryRequest request) {

        /*
         * If the agent already supplied a state,
         * trust it.
         */
        if (request.getState() != null) {
            return request.getState();
        }

        String eventType =
                request.getEventType();

        if (eventType == null ||
                eventType.isBlank()) {

            return null;
        }

        switch (eventType.toUpperCase()) {

            case "THREAD_START":
                return ThreadState.RUNNABLE;

            case "THREAD_BLOCKED":
                return ThreadState.BLOCKED;

            case "THREAD_WAITING":
                return ThreadState.WAITING;

            case "THREAD_TIMED_WAITING":
                return ThreadState.TIMED_WAITING;

            case "THREAD_PARKED":
                return ThreadState.PARKED;

            case "THREAD_END":
            case "THREAD_TERMINATED":
                return ThreadState.TERMINATED;

            /*
             * These events do not necessarily represent
             * a state transition.
             */
            case "LOCK_OWNED":
            case "LOCK_ACQUIRE":
            case "LOCK_RELEASE":
            case "PINNING":
                return null;

            default:
                return null;
        }
    }
}