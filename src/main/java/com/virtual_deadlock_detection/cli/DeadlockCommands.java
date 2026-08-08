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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
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
 *
 * <p>Built on the Spring Shell 3.4 command model ({@code @Command} from
 * {@code org.springframework.shell.command.annotation}, replacing the removed
 * {@code @ShellComponent}/{@code @ShellMethod}/{@code @ShellOption} annotations
 * from v2). Output goes through {@code ctx.getTerminal().writer()} rather than
 * raw {@code System.out} — the 3.x {@link CommandContext} has no
 * {@code outputWriter()} shortcut (that was added in Spring Shell 4's core
 * module) — which stays correctly wired to the active terminal/writer in both
 * interactive and non-interactive shell modes, and is what Spring Shell's own
 * test harness expects.</p>
 */
@Component
@Transactional
@Command(group = "Deadlock Commands")
public class DeadlockCommands {

    private static final Logger log = LoggerFactory.getLogger(DeadlockCommands.class);
    private static final String GROUP = "Deadlock Commands";

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
    @Command(command = "status", description = "Show full diagnostic status (HEALTHY / WARNING / CRITICAL)", group = GROUP)
    public void status(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            long startNs = System.nanoTime();

            List<ThreadEntity>   threads = threadService.getAllThreads();
            List<LockEntity>     locks   = lockService.getAllLocks();
            List<WaitEdgeEntity> edges   = waitEdgeService.getAllEdges();

            WaitForGraph graph = buildGraphFromDb(edges);
            List<DeadlockCycle> cycles = RuntimeContext.DETECTOR.detect(graph);

            long pinnedCount = threads.stream()
                    .filter(t -> t.getCarrierThread() != null && !t.getCarrierThread().isEmpty())
                    .filter(t -> t.getState() == ThreadState.BLOCKED
                            || t.getState() == ThreadState.WAITING
                            || t.getState() == ThreadState.TIMED_WAITING)
                    .count();

            double scanMs = (System.nanoTime() - startNs) / 1_000_000.0;

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

            out.println(sb);
            out.flush();
        } catch (DataAccessException e) {
            log.error("status command failed: database access error", e);
            printError(out, "Unable to read status — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks list
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Lists all stored deadlock reports from the database.
     *
     * <pre>vdd:> deadlocks list</pre>
     */
    @Command(command = { "deadlocks", "list" }, alias = { "deadlock list", "deadlock" }, description = "List all stored deadlock reports", group = GROUP)
    public void deadlocksList(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            List<DeadlockReportDTO> reports = deadlockReportService.getAllReports();

            if (reports.isEmpty()) {
                out.println(GREEN + "  No deadlock reports found in database." + RESET);
                out.flush();
                return;
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
                        r.getId(), "", sevColored, "", resolved,
                        r.getDetectedAt() != null ? r.getDetectedAt().toString() : "-",
                        threads));
            }

            sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(reports.size()).append("\n");
            out.println(sb);
            out.flush();
        } catch (DataAccessException e) {
            log.error("deadlocks list command failed: database access error", e);
            printError(out, "Unable to list deadlock reports — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks detect
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Runs live deadlock detection on the current in-memory WaitForGraph.
     *
     * <pre>vdd:> deadlocks detect</pre>
     */
    @Command(command = { "deadlocks", "detect" }, alias = { "deadlock detect" }, description = "Run live deadlock detection on the current WaitForGraph", group = GROUP)
    public void deadlocksDetect(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            List<DeadlockCycle> cycles = graphService.detectDeadlocks();

            if (cycles.isEmpty()) {
                out.println(GREEN + BOLD + "\n  ✔  No deadlocks detected in current WaitForGraph.\n" + RESET);
                out.flush();
                return;
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

            out.println(sb);
            out.flush();
        } catch (RuntimeException e) {
            log.error("deadlocks detect command failed", e);
            printError(out, "Detection failed unexpectedly — see application logs for details.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks seed
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Seeds the database with a demo 2-way deadlock scenario.
     *
     * <pre>vdd:> deadlocks seed</pre>
     */
    @Command(command = { "deadlocks", "seed" }, alias = { "deadlock seed" }, description = "Seed the database with a demo 2-way deadlock scenario", group = GROUP)
    public void deadlocksSeed(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            waitEdgeRepository.deleteAll();
            lockRepository.deleteAll();
            threadRepository.deleteAll();

            ThreadEntity t1 = saveThread(10023L, "VirtualThread-10023", ThreadState.BLOCKED, "ForkJoinPool-1-worker-1");
            ThreadEntity t2 = saveThread(10024L, "VirtualThread-10024", ThreadState.BLOCKED, "ForkJoinPool-1-worker-2");

            ThreadEntity p1 = saveThread(2041L, "VirtualThread-2041", ThreadState.BLOCKED, "ForkJoinPool-1-worker-3");
            ThreadEntity p2 = saveThread(2089L, "VirtualThread-2089", ThreadState.BLOCKED, "ForkJoinPool-1-worker-7");

            LockEntity lock1 = saveLock("Monitor-2351897819504", "synchronized block", t2);
            LockEntity lock2 = saveLock("Monitor-2351897819505", "synchronized block", t1);

            ThreadEntity c1 = saveThread(99991L, "CarrierOwner-1", ThreadState.RUNNABLE, null);
            ThreadEntity c2 = saveThread(99992L, "CarrierOwner-2", ThreadState.RUNNABLE, null);
            LockEntity pl1 = saveLock("java.lang.Object@12abc3", "synchronized block", c1);
            LockEntity pl2 = saveLock("java.lang.Object@5f99ee", "synchronized block", c2);

            saveEdge(t1, lock1);
            saveEdge(t2, lock2);
            saveEdge(p1, pl1);
            saveEdge(p2, pl2);

            out.println(YELLOW + BOLD + "\n  ✔  2-way deadlock seeded. Run 'status' or 'deadlocks detect' to see it.\n" + RESET);
            out.flush();
        } catch (DataAccessException e) {
            log.error("deadlocks seed command failed: database access error", e);
            printError(out, "Failed to seed demo data — database is unreachable or write failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // deadlocks clear
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Clears all threads, locks, and wait-edges from the database.
     *
     * <pre>vdd:> deadlocks clear</pre>
     */
    @Command(command = { "deadlocks", "clear" }, alias = { "deadlock clear", "clear-data" }, description = "Clear all threads, locks, and wait-edges from the database", group = GROUP)
    public void deadlocksClear(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            waitEdgeRepository.deleteAll();
            lockRepository.deleteAll();
            threadRepository.deleteAll();
            out.println(GREEN + BOLD + "\n  ✔  Database cleared. Status: HEALTHY.\n" + RESET);
            out.flush();
        } catch (DataAccessException e) {
            log.error("deadlocks clear command failed: database access error", e);
            printError(out, "Failed to clear database — database is unreachable or write failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private WaitForGraph buildGraphFromDb(List<WaitEdgeEntity> edges) {
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
        return graph;
    }

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

    private void printError(PrintWriter out, String message) {
        out.println(RED + BOLD + "  ✘  " + message + RESET);
        out.flush();
    }
}