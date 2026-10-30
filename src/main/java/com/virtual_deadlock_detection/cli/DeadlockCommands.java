package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.dto.DeadlockReportDTO;
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
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.sun.tools.attach.VirtualMachine;

/**
 * CLI commands for deadlock detection, status monitoring, and data management.
 *
 * <pre>
 *   vdd:> status
 *   vdd:> deadlocks list
 *   vdd:> deadlocks detect
 *   vdd:> deadlocks clear
 * </pre>
 *
 * All telemetry displayed by this CLI comes from the VDD backend database.
 *
 * Threads, locks and wait-edges are populated by the instrumented target JVM.
 * Deadlock reports are populated by the deadlock reporting pipeline.
 */
@Component
public class DeadlockCommands {

    private static final Logger log =
            LoggerFactory.getLogger(DeadlockCommands.class);

    private static final String GROUP = "Deadlock Commands";

    // ANSI colour codes
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

    public DeadlockCommands(
            DeadlockReportService deadlockReportService,
            GraphService graphService,
            ThreadService threadService,
            LockService lockService,
            WaitEdgeService waitEdgeService) {

        this.deadlockReportService = deadlockReportService;
        this.graphService = graphService;
        this.threadService = threadService;
        this.lockService = lockService;
        this.waitEdgeService = waitEdgeService;
    }

    // -------------------------------------------------------------------------
    // status
    // -------------------------------------------------------------------------

    @Command(
            name = "status",
            description = "Show full diagnostic status (HEALTHY / WARNING / CRITICAL)",
            group = GROUP
    )
    public void status(CommandContext ctx) {

        PrintWriter out = ctx.outputWriter();

        try {

            long startNs = System.nanoTime();

            List<ThreadEntity> threads =
                    threadService.getAllThreads();

            List<LockEntity> locks =
                    lockService.getAllLocks();

            List<WaitEdgeEntity> edges =
                    waitEdgeService.getAllEdges();

            WaitForGraph graph =
                    graphService.buildGraphFromDb();

            List<DeadlockCycle> cycles =
                    RuntimeContext.DETECTOR.detect(graph);

            long pinnedCount = threads.stream()
                    .filter(t ->
                            t.getCarrierThread() != null &&
                                    !t.getCarrierThread().isEmpty()
                    )
                    .filter(t ->
                            t.getState() == ThreadState.BLOCKED ||
                                    t.getState() == ThreadState.WAITING ||
                                    t.getState() == ThreadState.TIMED_WAITING
                    )
                    .count();

            double scanMs =
                    (System.nanoTime() - startNs) / 1_000_000.0;

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

            sb.append("\n")
                    .append(CYAN)
                    .append(BOLD)
                    .append("╔══════════════════════════════════════════╗\n")
                    .append("║   Virtual Deadlock Detector — Status     ║\n")
                    .append("╚══════════════════════════════════════════╝")
                    .append(RESET)
                    .append("\n\n");

            sb.append(WHITE)
                    .append("  Timestamp   : ")
                    .append(RESET)
                    .append(Instant.now())
                    .append("\n");

            sb.append(WHITE)
                    .append("  Status      : ")
                    .append(RESET)
                    .append(statusColor)
                    .append(BOLD)
                    .append(statusLabel)
                    .append(RESET)
                    .append("\n\n");

            sb.append(WHITE)
                    .append("  Threads     : ")
                    .append(RESET)
                    .append(threads.size())
                    .append("\n");

            sb.append(WHITE)
                    .append("  Locks       : ")
                    .append(RESET)
                    .append(locks.size())
                    .append("\n");

            sb.append(WHITE)
                    .append("  Wait Edges  : ")
                    .append(RESET)
                    .append(edges.size())
                    .append("\n");

            sb.append(WHITE)
                    .append("  Deadlocks   : ")
                    .append(RESET)
                    .append(
                            cycles.isEmpty()
                                    ? GREEN + "0" + RESET
                                    : RED + BOLD + cycles.size() + RESET
                    )
                    .append("\n");

            sb.append(WHITE)
                    .append("  Pinned VTs  : ")
                    .append(RESET)
                    .append(
                            pinnedCount == 0
                                    ? GREEN + "0" + RESET
                                    : YELLOW + BOLD + pinnedCount + RESET
                    )
                    .append("\n");

            sb.append(WHITE)
                    .append("  Scan time   : ")
                    .append(RESET)
                    .append(String.format("%.2f ms", scanMs))
                    .append("\n");

            if (!cycles.isEmpty()) {

                sb.append("\n")
                        .append(RED)
                        .append(BOLD)
                        .append("  ⚠  Deadlock Cycles Detected:\n")
                        .append(RESET);

                for (int i = 0; i < cycles.size(); i++) {

                    DeadlockCycle cycle = cycles.get(i);

                    sb.append(RED)
                            .append("  Cycle ")
                            .append(i + 1)
                            .append(":\n")
                            .append(RESET);

                    for (GraphEdge e : cycle.getEdgesInCycle()) {

                        sb.append("    ")
                                .append(e.getSource().getLabel())
                                .append(YELLOW)
                                .append("  ──waits-for──>  ")
                                .append(RESET)
                                .append(e.getTarget().getLabel())
                                .append("\n");
                    }
                }
            }

            out.println(sb);
            out.flush();

        } catch (DataAccessException e) {

            log.error(
                    "status command failed: database access error",
                    e
            );

            printError(
                    out,
                    "Unable to read status — database is unreachable or query failed."
            );
        }
    }

    // -------------------------------------------------------------------------
    // deadlocks list
    // -------------------------------------------------------------------------

    @Command(
            name = { "deadlocks", "list" },
            description = "List latest stored deadlock reports",
            group = GROUP
    )
    public void deadlocksList(CommandContext ctx) {

        PrintWriter out = ctx.outputWriter();

        try {

            List<DeadlockReportDTO> reports =
                    deadlockReportService.getAllReports();

            if (reports.isEmpty()) {

                out.println(
                        GREEN +
                                "  No deadlock reports found in database." +
                                RESET
                );

                out.flush();
                return;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("\n")
                    .append(CYAN)
                    .append(BOLD)
                    .append(
                            String.format(
                                    "  %-6s  %-10s  %-8s  %-16s  %s\n",
                                    "ID",
                                    "SEVERITY",
                                    "RESOLVED",
                                    "DETECTED AT",
                                    "THREADS"
                            )
                    )
                    .append(RESET);

            sb.append(CYAN)
                    .append("  ")
                    .append("─".repeat(70))
                    .append(RESET)
                    .append("\n");

            for (DeadlockReportDTO r : reports) {

                String sev = r.getSeverity();

                String sevPadded =
                        String.format("%-10s", sev);

                String sevColored =
                        "CRITICAL".equalsIgnoreCase(sev)
                                ? RED + BOLD + sevPadded + RESET
                                : "WARNING".equalsIgnoreCase(sev)
                                ? YELLOW + BOLD + sevPadded + RESET
                                : GREEN + sevPadded + RESET;

                String resolvedPadded =
                        String.format(
                                "%-8s",
                                r.isResolved() ? "YES" : "NO"
                        );

                String resolved =
                        r.isResolved()
                                ? GREEN + resolvedPadded + RESET
                                : YELLOW + resolvedPadded + RESET;

                String threads =
                        r.getThreads() == null
                                ? "-"
                                : String.join(", ", r.getThreads());

                sb.append(
                        String.format(
                                "  %-6d  %s  %s  %-16s  %s\n",
                                r.getId(),
                                sevColored,
                                resolved,
                                r.getDetectedAt() != null
                                        ? r.getDetectedAt().toString()
                                        : "-",
                                threads
                        )
                );
            }

            sb.append("\n")
                    .append(WHITE)
                    .append("  Showing latest ")
                    .append(RESET)
                    .append(reports.size())
                    .append(WHITE)
                    .append(" report(s).\n")
                    .append(RESET);

            out.println(sb);
            out.flush();

        } catch (DataAccessException e) {

            log.error(
                    "deadlocks list command failed: database access error",
                    e
            );

            printError(
                    out,
                    "Unable to list deadlock reports — database is unreachable or query failed."
            );
        }
    }

    // -------------------------------------------------------------------------
    // deadlocks detect
    // -------------------------------------------------------------------------

    @Command(
            name = { "deadlocks", "detect" },
            description = "Run live deadlock detection on the current WaitForGraph",
            group = GROUP
    )
    public void deadlocksDetect(CommandContext ctx) {

        PrintWriter out = ctx.outputWriter();

        try {

            List<DeadlockCycle> cycles =
                    graphService.detectDeadlocks();

            if (cycles.isEmpty()) {

                out.println(
                        GREEN +
                                BOLD +
                                "\n  ✔  No deadlocks detected in current WaitForGraph.\n" +
                                RESET
                );

                out.flush();
                return;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("\n")
                    .append(RED)
                    .append(BOLD)
                    .append("  ✘  ")
                    .append(cycles.size())
                    .append(" deadlock cycle(s) detected!\n\n")
                    .append(RESET);

            for (int i = 0; i < cycles.size(); i++) {

                sb.append(RED)
                        .append(BOLD)
                        .append("  Cycle ")
                        .append(i + 1)
                        .append(":\n")
                        .append(RESET);

                for (GraphEdge edge :
                        cycles.get(i).getEdgesInCycle()) {

                    sb.append("    ")
                            .append(CYAN)
                            .append(edge.getSource().getLabel())
                            .append(RESET)
                            .append(YELLOW)
                            .append("  ──waits-for──>  ")
                            .append(RESET)
                            .append(CYAN)
                            .append(edge.getTarget().getLabel())
                            .append(RESET)
                            .append("\n");
                }

                sb.append("\n");
            }

            out.println(sb);
            out.flush();

        } catch (RuntimeException e) {

            log.error(
                    "deadlocks detect command failed",
                    e
            );

            printError(
                    out,
                    "Detection failed unexpectedly — see application logs for details."
            );
        }
    }

        @Command(
                        name = "detect",
                        description = "Attach to or launch a JVM and stream live deadlock detection",
                        group = GROUP
        )
        public void detect(
                        CommandContext ctx,
                        @Option(longName = "pid", description = "PID of an already running JVM") String pid,
                        @Option(longName = "jar", description = "Path to a runnable jar to launch") String jar,
                        @Option(longName = "json", description = "Print machine-readable JSON") boolean json) {

                PrintWriter out = ctx.outputWriter();
                Process target = null;
                boolean nonInteractive = Boolean.getBoolean("dixy.noninteractive");

                try {
                        String serverUrl = System.getProperty("dixy.server.url", "http://localhost:8081");
                        String agentPath = System.getProperty("dixy.agent.path");

                        if (pid != null && !pid.isBlank()) {
                                requireAgentPath(agentPath);
                                VirtualMachine vm = VirtualMachine.attach(pid);
                                try {
                                        vm.loadAgent(agentPath, "serverUrl=" + serverUrl + ",apiKey="
                                                        + System.getProperty("security.api-key", "dixy-local-dev-key-123")
                                                        + ",appName=attached-" + pid);
                                } finally {
                                        vm.detach();
                                }
                        } else if (jar != null && !jar.isBlank()) {
                                requireAgentPath(agentPath);
                                target = new ProcessBuilder(javaCommand(), "-javaagent:" + agentPath
                                                + "=serverUrl=" + serverUrl + ",apiKey="
                                                + System.getProperty("security.api-key", "dixy-local-dev-key-123")
                                                + ",appName=" + Path.of(jar).getFileName(), "-jar", jar)
                                                .inheritIO()
                                                .start();
                        } else if (!json) {
                                out.println("DIXY is monitoring the configured live backend. Press Ctrl+C to stop.");
                                out.flush();
                        }

                        boolean found = streamDetection(ctx, json, target);
                        if (found && nonInteractive) {
                                System.exit(1);
                        }
                } catch (Exception e) {
                        out.println("DIXY detect failed: " + e.getMessage());
                        out.flush();
                        if (nonInteractive) {
                                System.exit(2);
                        }
                } finally {
                        if (target != null && target.isAlive()) {
                                target.destroy();
                        }
                }
        }

        private void requireAgentPath(String agentPath) {
                if (agentPath == null || agentPath.isBlank()) {
                        throw new IllegalStateException("DIXY agent path is required for --pid or --jar.");
                }
        }

        private boolean streamDetection(CommandContext ctx, boolean json, Process target)
                        throws InterruptedException {
                PrintWriter out = ctx.outputWriter();
                boolean reported = false;
                while (target == null || target.isAlive()) {
                        List<DeadlockCycle> cycles = graphService.detectDeadlocks();
                        List<WaitEdgeEntity> edges = waitEdgeService.getAllEdges();
                        if (json) {
                                out.println(toJson(cycles, edges));
                        } else {
                                out.printf("[%s] threads=%d locks=%d waits=%d cycles=%d%n",
                                                Instant.now(), threadService.getAllThreads().size(),
                                                lockService.getAllLocks().size(), edges.size(), cycles.size());
                                printCycles(out, cycles, edges);
                        }
                        out.flush();
                        if (!cycles.isEmpty()) {
                                reported = true;
                                break;
                        }
                        TimeUnit.SECONDS.sleep(1);
                }
                return reported;
        }

        private void printCycles(PrintWriter out, List<DeadlockCycle> cycles,
                                                         List<WaitEdgeEntity> edges) {
                for (int i = 0; i < cycles.size(); i++) {
                        out.println(RED + BOLD + "ALERT: DEADLOCK CYCLE " + (i + 1) + RESET);
                        for (GraphEdge edge : cycles.get(i).getEdgesInCycle()) {
                                String lock = edges.stream()
                                                .filter(e -> e.getWaitingThread() != null && e.getLock() != null
                                                                && e.getLock().getOwnerThread() != null
                                                                && edge.getSource().getLabel().equals(e.getWaitingThread().getThreadName())
                                                                && edge.getTarget().getLabel().equals(e.getLock().getOwnerThread().getThreadName()))
                                                .map(e -> e.getLock().getLockId()).findFirst().orElse("unknown-lock");
                                out.printf("  %s holds %s; %s waits for it%n", edge.getTarget().getLabel(), lock,
                                                edge.getSource().getLabel());
                        }
                }
        }

        private String toJson(List<DeadlockCycle> cycles, List<WaitEdgeEntity> edges) {
                StringBuilder json = new StringBuilder("{\"cycles\":[");
                for (int i = 0; i < cycles.size(); i++) {
                        if (i > 0) json.append(',');
                        json.append("{\"edges\":[");
                        List<GraphEdge> cycleEdges = cycles.get(i).getEdgesInCycle();
                        for (int j = 0; j < cycleEdges.size(); j++) {
                                if (j > 0) json.append(',');
                                GraphEdge edge = cycleEdges.get(j);
                                json.append("{\"from\":\"").append(escape(edge.getSource().getLabel()))
                                                .append("\",\"to\":\"").append(escape(edge.getTarget().getLabel()))
                                                .append("\"}");
                        }
                        json.append("]}");
                }
                return json.append("]}").toString();
        }

        private String escape(String value) {
                return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String javaCommand() {
                String javaHome = System.getenv("JAVA_HOME");
                if (javaHome != null && !javaHome.isBlank()) {
                        return Path.of(javaHome, "bin", "java").toString();
                }
                return "java";
        }

    // -------------------------------------------------------------------------
    // deadlocks clear
    // -------------------------------------------------------------------------

    /**
     * Clears all VDD telemetry data.
     *
     * Uses one PostgreSQL TRUNCATE operation through
     * DeadlockReportService instead of Hibernate row-by-row
     * deletion. This is important because the database can
     * contain millions of telemetry rows.
     *
     * <pre>vdd:> deadlocks clear</pre>
     */
    @Command(
            name = { "deadlocks", "clear" },
            description = "Clear all VDD telemetry data",
            group = GROUP
    )
    public void deadlocksClear(CommandContext ctx) {

        PrintWriter out = ctx.outputWriter();

        try {

            deadlockReportService.clearAllReports();

            out.println(
                    GREEN +
                            BOLD +
                            "\n  ✔  Database cleared. Status: HEALTHY.\n" +
                            RESET
            );

            out.flush();

        } catch (DataAccessException e) {

            log.error(
                    "deadlocks clear command failed: database access error",
                    e
            );

            printError(
                    out,
                    "Failed to clear database — database is unreachable or write failed."
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void printError(
            PrintWriter out,
            String message) {

        out.println(
                RED +
                        BOLD +
                        "  ✘  " +
                        message +
                        RESET
        );

        out.flush();
    }
}