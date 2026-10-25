package com.virtual_deadlock_detection.LockDetectionAgent.runtime;

/**
 * Identifies threads that belong to the DIXY agent itself (JFR streaming,
 * the HTTP client used to ship telemetry, the poller).
 *
 * Why this matters: every telemetry POST spins up java.net.http worker
 * threads, those threads take monitors, the JVMTI hook reports that
 * contention, and the report triggers another POST. Left unfiltered the
 * agent feeds on its own output and reports "deadlocks" between its own
 * HttpClient workers.
 *
 * This is applied once, at the single choke point every event passes
 * through (EventCollector), instead of in each producer separately.
 */
public final class InternalThreads {

    private InternalThreads() {
    }

    public static boolean isInternal(String threadName) {
        if (threadName == null) {
            return false;
        }
        return threadName.startsWith("JFR ")
                || threadName.startsWith("HttpClient-")
                || threadName.startsWith("vld-")
                || threadName.startsWith("VDD-")
                || threadName.startsWith("vdd-")
                || threadName.startsWith("dixy-");
    }
}
