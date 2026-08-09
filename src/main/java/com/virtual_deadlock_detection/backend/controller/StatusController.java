package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.backend.service.GraphService;
import com.virtual_deadlock_detection.backend.service.LockService;
import com.virtual_deadlock_detection.backend.service.ThreadService;
import com.virtual_deadlock_detection.backend.service.WaitEdgeService;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/status")
@CrossOrigin(origins = "*")
public class StatusController {

    private final ThreadService threadService;
    private final LockService lockService;
    private final WaitEdgeService waitEdgeService;
    private final GraphService graphService;
    private final ThreadRepository threadRepository;
    private final LockRepository lockRepository;
    private final WaitEdgeRepository waitEdgeRepository;

    public StatusController(ThreadService threadService,
                            LockService lockService,
                            WaitEdgeService waitEdgeService,
                            GraphService graphService,
                            ThreadRepository threadRepository,
                            LockRepository lockRepository,
                            WaitEdgeRepository waitEdgeRepository) {
        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
        this.graphService = graphService;
        this.threadRepository = threadRepository;
        this.lockRepository = lockRepository;
        this.waitEdgeRepository = waitEdgeRepository;
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearStatus() {
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();
        return ResponseEntity.ok("JVM Database Cleared. Status: HEALTHY.");
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedStatus() {
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();

        // 1. Create Deadlocked Threads
        ThreadEntity t1 = createThread(10023L, "VirtualThread-10023", ThreadState.BLOCKED, "ForkJoinPool-1-worker-1");
        ThreadEntity t2 = createThread(10024L, "VirtualThread-10024", ThreadState.BLOCKED, "ForkJoinPool-1-worker-2");


        // 2. Create Pinned Threads (Warnings)
        ThreadEntity p1 = createThread(2041L, "VirtualThread-2041", ThreadState.BLOCKED, "ForkJoinPool-1-worker-3");
        ThreadEntity p2 = createThread(2089L, "VirtualThread-2089", ThreadState.BLOCKED, "ForkJoinPool-1-worker-7");

        // 3. Create locks owned by deadlocked threads
        LockEntity lock1 = createLock("Monitor-2351897819504", "synchronized block", t2);
        LockEntity lock2 = createLock("Monitor-2351897819505", "synchronized block", t1);

        // 4. Create locks owned by other threads for pinning
        ThreadEntity carrierOwner1 = createThread(99991L, "CarrierOwner-1", ThreadState.RUNNABLE, null);
        ThreadEntity carrierOwner2 = createThread(99992L, "CarrierOwner-2", ThreadState.RUNNABLE, null);
        LockEntity pinLock1 = createLock("java.lang.Object@12abc3", "synchronized block", carrierOwner1);
        LockEntity pinLock2 = createLock("java.lang.Object@5f99ee", "synchronized block", carrierOwner2);

        // 5. Create Wait Edges to form the circular deadlock
        createWaitEdge(t1, lock1);
        createWaitEdge(t2, lock2);

        // 6. Create Wait Edges for pinning
        createWaitEdge(p1, pinLock1);
        createWaitEdge(p2, pinLock2);

        return ResponseEntity.ok("2-Way Deadlock Seeded. Status: CRITICAL.");
    }

    private ThreadEntity createThread(Long threadId, String threadName, ThreadState state, String carrierThread) {
        ThreadEntity thread = new ThreadEntity();
        thread.setThreadId(threadId);
        thread.setThreadName(threadName);
        thread.setState(state);
        thread.setCarrierThread(carrierThread);
        return threadRepository.save(thread);
    }

    private LockEntity createLock(String lockId, String lockType, ThreadEntity owner) {
        LockEntity lock = new LockEntity();
        lock.setLockId(lockId);
        lock.setLockType(lockType);
        lock.setOwnerThread(owner);
        return lockRepository.save(lock);
    }

    private void createWaitEdge(ThreadEntity waiting, LockEntity lock) {
        WaitEdgeEntity edge = new WaitEdgeEntity();
        edge.setWaitingThread(waiting);
        edge.setLock(lock);
        waitEdgeRepository.save(edge);
    }

    @GetMapping
    public DiagnosticResponse getStatus() {
        long startTime = System.nanoTime();

        List<ThreadEntity> dbThreads = threadService.getAllThreads();
        List<LockEntity> dbLocks = lockService.getAllLocks();
        List<WaitEdgeEntity> dbEdges = waitEdgeService.getAllEdges();

        // 1. Build WaitForGraph from wait edges — delegates to GraphService
        // so the web dashboard and the CLI's "status"/"deadlocks detect"
        // always agree on how the graph is built, instead of keeping a
        // second, drifted copy of this logic here.
        WaitForGraph graph = graphService.buildGraphFromDb();

        // 2. Detect Deadlocks
        List<DeadlockCycle> cycles = RuntimeContext.DETECTOR.detect(graph);

        // 3. Map Deadlock Cycles to Frontend structure
        List<DeadlockEdgeDTO> deadlockEdges = new ArrayList<>();
        for (DeadlockCycle cycle : cycles) {
            for (GraphEdge edge : cycle.getEdgesInCycle()) {
                long fromId = edge.getSource().getId();
                long toId = edge.getTarget().getId();

                String waitingOnLockStr = "Unknown Lock";
                boolean isPinned = false;

                // Lookup matching WaitEdgeEntity to get lock details
                for (WaitEdgeEntity edgeEntity : dbEdges) {
                    if (edgeEntity.getWaitingThread().getThreadId() == fromId &&
                            edgeEntity.getLock().getOwnerThread() != null &&
                            edgeEntity.getLock().getOwnerThread().getThreadId() == toId) {

                        waitingOnLockStr = edgeEntity.getLock().getLockId() + " (" + edgeEntity.getLock().getLockType() + ")";
                        if ("synchronized".equalsIgnoreCase(edgeEntity.getLock().getLockType()) ||
                                edgeEntity.getLock().getLockType().toLowerCase().contains("synchronized")) {
                            isPinned = true;
                        }
                        break;
                    }
                }

                DeadlockEdgeDTO edgeDto = new DeadlockEdgeDTO(
                        edge.getSource().getLabel(),
                        waitingOnLockStr,
                        edge.getTarget().getLabel(),
                        isPinned
                );
                deadlockEdges.add(edgeDto);
            }
        }

        // 4. Map Pinned Threads to Frontend structure
        List<PinningDetailDTO> pinningDetails = new ArrayList<>();
        for (ThreadEntity thread : dbThreads) {
            if (thread.getCarrierThread() != null && !thread.getCarrierThread().isEmpty()) {
                boolean isBlockedOrWaiting = thread.getState() == ThreadState.BLOCKED ||
                        thread.getState() == ThreadState.WAITING ||
                        thread.getState() == ThreadState.TIMED_WAITING;
                if (isBlockedOrWaiting) {
                    String lockType = "synchronized block";
                    for (WaitEdgeEntity edgeEntity : dbEdges) {
                        if (edgeEntity.getWaitingThread().getThreadId().equals(thread.getThreadId())) {
                            lockType = edgeEntity.getLock().getLockType();
                            break;
                        }
                    }

                    PinningDetailDTO pinDto = new PinningDetailDTO(
                            thread.getThreadName(),
                            thread.getCarrierThread(),
                            lockType,
                            null // no real source-location capture wired up yet — was
                            // previously a hardcoded fake stack frame; better to
                            // report "unknown" than to fabricate one.
                    );
                    pinningDetails.add(pinDto);
                }
            }
        }

        // 5. Determine Overall status
        String status = "HEALTHY";
        if (!deadlockEdges.isEmpty()) {
            status = "CRITICAL";
        } else if (!pinningDetails.isEmpty()) {
            status = "WARNING";
        }

        // 6. Build Metrics
        long endTime = System.nanoTime();
        double scanTimeMs = (endTime - startTime) / 1_000_000.0;
        scanTimeMs = Math.round(scanTimeMs * 100.0) / 100.0; // round to 2 decimals
        if (scanTimeMs <= 0.0) {
            scanTimeMs = 0.15; // default minimum
        }

        long virtualThreadsCount = dbThreads.stream()
                .filter(t -> t.getThreadName().toLowerCase().contains("virtual"))
                .count();
        if (virtualThreadsCount == 0 && !dbThreads.isEmpty()) {
            virtualThreadsCount = dbThreads.size();
        }

        long distinctCarriers = dbThreads.stream()
                .map(ThreadEntity::getCarrierThread)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .count();

        // Real default carrier pool size for virtual threads is the common
        // ForkJoinPool's target parallelism (== available processors unless
        // overridden by -Djdk.virtualThreadScheduler.parallelism). This
        // replaces the previous hardcoded "/ 16" and the empty-DB fallback
        // that reported fake numbers (2,450,122 virtual threads, 16
        // carriers) — those were purely cosmetic and not derived from
        // anything real. An empty database now honestly reports 0.
        int carrierPoolTarget = java.util.concurrent.ForkJoinPool.getCommonPoolParallelism();
        String carrierPoolSize = distinctCarriers + " / " + carrierPoolTarget;

        double heapUsagePercent = 0.0;
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        if (maxMemory > 0) {
            heapUsagePercent = (double) (totalMemory - freeMemory) / maxMemory * 100.0;
        }
        heapUsagePercent = Math.round(heapUsagePercent * 10.0) / 10.0;

        TelemetryMetricsDTO metrics = new TelemetryMetricsDTO(
                virtualThreadsCount,
                carrierPoolSize,
                dbLocks.size(),
                dbEdges.size(),
                scanTimeMs,
                heapUsagePercent
        );

        return new DiagnosticResponse(
                status,
                Instant.now().toString(),
                metrics,
                deadlockEdges,
                pinningDetails
        );
    }

    // Helper DTOs matching frontend interfaces

    public static class DiagnosticResponse {
        private final String status;
        private final String timestamp;
        private final TelemetryMetricsDTO metrics;
        private final List<DeadlockEdgeDTO> deadlocks;
        private final List<PinningDetailDTO> pinning;

        public DiagnosticResponse(String status, String timestamp, TelemetryMetricsDTO metrics,
                                  List<DeadlockEdgeDTO> deadlocks, List<PinningDetailDTO> pinning) {
            this.status = status;
            this.timestamp = timestamp;
            this.metrics = metrics;
            this.deadlocks = deadlocks;
            this.pinning = pinning;
        }

        public String getStatus() { return status; }
        public String getTimestamp() { return timestamp; }
        public TelemetryMetricsDTO getMetrics() { return metrics; }
        public List<DeadlockEdgeDTO> getDeadlocks() { return deadlocks; }
        public List<PinningDetailDTO> getPinning() { return pinning; }
    }

    public static class TelemetryMetricsDTO {
        private final long virtualThreads;
        private final String carrierPoolSize;
        private final long activeMonitors;
        private final long scannedEdges;
        private final double scanTimeMs;
        private final double heapUsagePercent;

        public TelemetryMetricsDTO(long virtualThreads, String carrierPoolSize, long activeMonitors,
                                   long scannedEdges, double scanTimeMs, double heapUsagePercent) {
            this.virtualThreads = virtualThreads;
            this.carrierPoolSize = carrierPoolSize;
            this.activeMonitors = activeMonitors;
            this.scannedEdges = scannedEdges;
            this.scanTimeMs = scanTimeMs;
            this.heapUsagePercent = heapUsagePercent;
        }

        public long getVirtualThreads() { return virtualThreads; }
        public String getCarrierPoolSize() { return carrierPoolSize; }
        public long getActiveMonitors() { return activeMonitors; }
        public long getScannedEdges() { return scannedEdges; }
        public double getScanTimeMs() { return scanTimeMs; }
        public double getHeapUsagePercent() { return heapUsagePercent; }
    }

    public static class DeadlockEdgeDTO {
        private final String fromThread;
        private final String waitingOnLock;
        private final String heldByThread;
        @JsonProperty("isPinned")
        private final boolean isPinned;

        public DeadlockEdgeDTO(String fromThread, String waitingOnLock, String heldByThread, boolean isPinned) {
            this.fromThread = fromThread;
            this.waitingOnLock = waitingOnLock;
            this.heldByThread = heldByThread;
            this.isPinned = isPinned;
        }

        public String getFromThread() { return fromThread; }
        public String getWaitingOnLock() { return waitingOnLock; }
        public String getHeldByThread() { return heldByThread; }
        @JsonProperty("isPinned")
        public boolean isPinned() { return isPinned; }
    }

    public static class PinningDetailDTO {
        private final String virtualThread;
        private final String carrierThread;
        private final String lockType;
        private final String sourceLocation;

        public PinningDetailDTO(String virtualThread, String carrierThread, String lockType, String sourceLocation) {
            this.virtualThread = virtualThread;
            this.carrierThread = carrierThread;
            this.lockType = lockType;
            this.sourceLocation = sourceLocation;
        }

        public String getVirtualThread() { return virtualThread; }
        public String getCarrierThread() { return carrierThread; }
        public String getLockType() { return lockType; }
        public String getSourceLocation() { return sourceLocation; }
    }
}