package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.dto.DeadlockReportDTO;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import com.virtual_deadlock_detection.backend.service.DeadlockReportService;
import com.virtual_deadlock_detection.backend.service.GraphService;
import com.virtual_deadlock_detection.backend.service.LockService;
import com.virtual_deadlock_detection.backend.service.ThreadService;
import com.virtual_deadlock_detection.backend.service.WaitEdgeService;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * CLI commands for deadlock detection, status monitoring, and data management.
 *
 * <pre>
 *   vdd:> status
 *   vdd:> deadlocks list
 *   vdd:> deadlocks detect
 *   vdd:> deadlocks seed
 *   vdd:> deadlocks clear
 * </pre>
 */
@ShellComponent
@Transactional
public class DeadlockCommands {


    // ── ANSI colour codes (works on most modern terminals) ──────────────────
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String WHITE  = "\u001B[37m";

    private final DeadlockReportService deadlockReportService;
    private final GraphService graphService;
    private final ThreadService threadService;
    private final LockService lockService;
    private final WaitEdgeService waitEdgeService;
    private final ThreadRepository threadRepository;
    private final LockRepository lockRepository;
    private final WaitEdgeRepository waitEdgeRepository;

    public DeadlockCommands(DeadlockReportService deadlockReportService,
                            GraphService graphService,
                            ThreadService threadService,
                            LockService lockService,
                            WaitEdgeService waitEdgeService,
                            ThreadRepository threadRepository,
                            LockRepository lockRepository,
                            WaitEdgeRepository waitEdgeRepository) {
        this.deadlockReportService = deadlockReportService;
        this.graphService = graphService;
        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
        this.threadRepository = threadRepository;
        this.lockRepository = lockRepository;
        this.waitEdgeRepository = waitEdgeRepository;
    }

    // ────────────────────────────────────────────────────────────────────────
    // status
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Prints a full diagnostic status report to the terminal.
     *
     * <pre>vdd:> status</pre>
     */
    @ShellMethod(value = "Show full diagnostic status (HEALTHY / WARNING / CRITICAL)", key = "status")
    public String status() {
        long startNs = System.nanoTime();

        List<ThreadEntity>   threads = threadService.getAllThreads();
        List<LockEntity>     locks   = lockService.getAllLocks();
        List<WaitEdgeEntity> edges   = waitEdgeService.getAllEdges();

        // Build WaitForGraph from DB state
        WaitForGraph graph = new WaitForGraph();
        for (WaitEdgeEntity edgeEntity : edges) {
            ThreadEntity waiting = edgeEntity.getWaitingThread();
            LockEntity   lock    = edgeEntity.getLock();
            if (waiting == null || lock == null) continue;
            ThreadEntity owner = lock.getOwnerThread();
            if (owner == null) continue;
            if (waiting.getThreadId().equals(owner.getThreadId())) continue;

            GraphNode src = new GraphNode(waiting.getThreadId(), NodeType.VIRTUAL_THREAD, waiting.getThreadName());
            GraphNode tgt = new GraphNode(owner.getThreadId(),   NodeType.VIRTUAL_THREAD, owner.getThreadName());
            graph.addEdge(new GraphEdge(src, tgt, EdgeType.WAITS_FOR));
        }

        List<DeadlockCycle> cycles = RuntimeContext.DETECTOR.detect(graph);

        long pinnedCount = threads.stream()
                .filter(t -> t.getCarrierThread() != null && !t.getCarrierThread().isEmpty())
                .filter(t -> t.getState() == ThreadState.BLOCKED
                          || t.getState() == ThreadState.WAITING
                          || t.getState() == ThreadState.TIMED_WAITING)
                .count();

        double scanMs = (System.nanoTime() - startNs) / 1_000_000.0;

        // Determine status
        String statusLabel;
        String statusColor;
        if (!cycles.isEmpty()) {
            statusLabel = "CRITICAL";
            statusColor = RED;
        } else if (pinnedCount > 0) {
            statusLabel = "WARNING";
            statusColor = YELLOW;
        } else {
            statusLabel = "HEALTHY";
            statusColor = GREEN;
        }

        // ── render ─────────────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD)
          .append("╔══════════════════════════════════════════╗\n")
          .append("║   Virtual Deadlock Detector — Status     ║\n")
          .append("╚══════════════════════════════════════════╝").append(RESET).append("\n\n");

        sb.append(WHITE).append("  Timestamp   : ").append(RESET).append(Instant.now()).append("\n");
        sb.append(WHITE).append("  Status      : ").append(RESET)
          .append(statusColor).append(BOLD).append(statusLabel).append(RESET).append("\n\n");

        sb.append(WHITE).append("  Threads     : ").append(RESET).append(threads.size()).append("\n");
        sb.append(WHITE).append("  Locks       : ").append(RESET).append(locks.size()).append("\n");
        sb.append(WHITE).append("  Wait Edges  : ").append(RESET).append(edges.size()).append("\n");
        sb.append(WHITE).append("  Deadlocks   : ").append(RESET)
          .append(cycles.isEmpty() ? GREEN + "0" + RESET : RED + BOLD + cycles.size() + RESET).append("\n");
        sb.append(WHITE).append("  Pinned VTs  : ").append(RESET)
          .append(pinnedCount == 0 ? GREEN + "0" + RESET : YELLOW + BOLD + pinnedCount + RESET).append("\n");
        sb.append(WHITE).append("  Scan time   : ").append(RESET)
          .append(String.format("%.2f ms", scanMs)).append("\n");

        // Deadlock detail
        if (!cycles.isEmpty()) {
            sb.append("\n").append(RED).append(BOLD).append("  ⚠  Deadlock Cycles Detected:\n").append(RESET);
            for (int i = 0; i < cycles.size(); i++) {
                DeadlockCycle cycle = cycles.get(i);
                sb.append(RED).append("  Cycle ").append(i + 1).append(":\n").append(RESET);
                for (GraphEdge e : cycle.getEdgesInCycle()) {
                    sb.append("    ")
                      .append(e.getSource().getLabel())
                      .append(YELLOW).append("  ──waits-for──>  ").append(RESET)
                      .append(e.getTarget().getLabel()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks list
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Lists all stored deadlock reports from the database.
     *
     * <pre>vdd:> deadlocks list</pre>
     */
    @ShellMethod(value = "List all stored deadlock reports", key = "deadlocks list")
    public String deadlocksList() {
        List<DeadlockReportDTO> reports = deadlockReportService.getAllReports();

        if (reports.isEmpty()) {
            return GREEN + "  No deadlock reports found in database." + RESET;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD)
          .append(String.format("  %-6s  %-10s  %-8s  %-16s  %s\n",
                  "ID", "SEVERITY", "RESOLVED", "DETECTED AT", "THREADS"))
          .append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(70)).append(RESET).append("\n");

        for (DeadlockReportDTO r : reports) {
            String sev = r.getSeverity();
            String sevColored = "CRITICAL".equalsIgnoreCase(sev) ? RED + BOLD + sev + RESET
                              : "WARNING".equalsIgnoreCase(sev)  ? YELLOW + BOLD + sev + RESET
                              : GREEN + sev + RESET;
            String resolved = r.isResolved() ? GREEN + "YES" + RESET : YELLOW + "NO" + RESET;
            String threads   = r.getThreads() == null ? "-" : String.join(", ", r.getThreads());

            sb.append(String.format("  %-6d  %s%-10s%s  %-8s  %-16s  %s\n",
                    r.getId(),
                    "", // colour already added
                    sevColored,
                    "",
                    resolved,
                    r.getDetectedAt() != null ? r.getDetectedAt().toString() : "-",
                    threads));
        }

        sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(reports.size()).append("\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks detect
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Runs live deadlock detection on the current in-memory WaitForGraph.
     *
     * <pre>vdd:> deadlocks detect</pre>
     */
    @ShellMethod(value = "Run live deadlock detection on the current WaitForGraph", key = "deadlocks detect")
    public String deadlocksDetect() {
        List<DeadlockCycle> cycles = graphService.detectDeadlocks();

        if (cycles.isEmpty()) {
            return GREEN + BOLD + "\n  ✔  No deadlocks detected in current WaitForGraph.\n" + RESET;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(RED).append(BOLD)
          .append("  ✘  ").append(cycles.size()).append(" deadlock cycle(s) detected!\n\n")
          .append(RESET);

        for (int i = 0; i < cycles.size(); i++) {
            sb.append(RED).append(BOLD).append("  Cycle ").append(i + 1).append(":\n").append(RESET);
            for (GraphEdge edge : cycles.get(i).getEdgesInCycle()) {
                sb.append("    ")
                  .append(CYAN).append(edge.getSource().getLabel()).append(RESET)
                  .append(YELLOW).append("  ──waits-for──>  ").append(RESET)
                  .append(CYAN).append(edge.getTarget().getLabel()).append(RESET)
                  .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks seed
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Seeds the database with a demo 2-way deadlock scenario.
     *
     * <pre>vdd:> deadlocks seed</pre>
     */
    @ShellMethod(value = "Seed the database with a demo 2-way deadlock scenario", key = "deadlocks seed")
    public String deadlocksSeed() {
        // Clear first
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();

        // Deadlocked threads
        ThreadEntity t1 = saveThread(10023L, "VirtualThread-10023", ThreadState.BLOCKED, "ForkJoinPool-1-worker-1");
        ThreadEntity t2 = saveThread(10024L, "VirtualThread-10024", ThreadState.BLOCKED, "ForkJoinPool-1-worker-2");


        // Pinned threads
        ThreadEntity p1 = saveThread(2041L, "VirtualThread-2041", ThreadState.BLOCKED, "ForkJoinPool-1-worker-3");
        ThreadEntity p2 = saveThread(2089L, "VirtualThread-2089", ThreadState.BLOCKED, "ForkJoinPool-1-worker-7");

        // Locks owned by deadlocked threads (circular)
        LockEntity lock1 = saveLock("Monitor-2351897819504", "synchronized block", t2);
        LockEntity lock2 = saveLock("Monitor-2351897819505", "synchronized block", t1);

        // Locks for pinned threads
        ThreadEntity c1 = saveThread(99991L, "CarrierOwner-1", ThreadState.RUNNABLE, null);
        ThreadEntity c2 = saveThread(99992L, "CarrierOwner-2", ThreadState.RUNNABLE, null);
        LockEntity pl1 = saveLock("java.lang.Object@12abc3", "synchronized block", c1);
        LockEntity pl2 = saveLock("java.lang.Object@5f99ee", "synchronized block", c2);

        // Wait edges
        saveEdge(t1, lock1);
        saveEdge(t2, lock2);
        saveEdge(p1, pl1);
        saveEdge(p2, pl2);

        return YELLOW + BOLD + "\n  ✔  2-way deadlock seeded. Run 'status' or 'deadlocks detect' to see it.\n" + RESET;
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks clear
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Clears all threads, locks, and wait-edges from the database.
     *
     * <pre>vdd:> deadlocks clear</pre>
     */
    @ShellMethod(value = "Clear all threads, locks, and wait-edges from the database", key = "deadlocks clear")
    public String deadlocksClear() {
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();
        return GREEN + BOLD + "\n  ✔  Database cleared. Status: HEALTHY.\n" + RESET;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private ThreadEntity saveThread(Long threadId, String name, ThreadState state, String carrier) {
        ThreadEntity t = new ThreadEntity();
        t.setThreadId(threadId);
        t.setThreadName(name);
        t.setState(state);
        t.setCarrierThread(carrier);
        return threadRepository.save(t);
    }

    private LockEntity saveLock(String lockId, String lockType, ThreadEntity owner) {
        LockEntity l = new LockEntity();
        l.setLockId(lockId);
        l.setLockType(lockType);
        l.setOwnerThread(owner);
        return lockRepository.save(l);
    }

    private void saveEdge(ThreadEntity waiting, LockEntity lock) {
        WaitEdgeEntity e = new WaitEdgeEntity();
        e.setWaitingThread(waiting);
        e.setLock(lock);
        waitEdgeRepository.save(e);
    }
}
