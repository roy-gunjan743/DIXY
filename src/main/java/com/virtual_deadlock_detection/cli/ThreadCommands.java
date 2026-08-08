package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.service.ThreadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

/**
 * CLI commands for inspecting and managing virtual threads stored in the database.
 *
 * <pre>
 *   vdd:> threads list
 *   vdd:> threads get --thread-id 10023
 *   vdd:> threads delete --id 5
 * </pre>
 *
 * <p>Built on the Spring Shell 3.4 command model ({@code @Command} / {@code @Option}
 * from {@code org.springframework.shell.command.annotation}). Output goes through
 * {@code ctx.getTerminal().writer()} rather than raw {@code System.out} — the
 * 3.x {@link CommandContext} has no {@code outputWriter()} shortcut (that was
 * added in Spring Shell 4's core module) — and database errors are caught and
 * reported as a clean one-line message instead of a raw stack trace surfacing
 * in the shell.</p>
 */
@Component
@Transactional
public class ThreadCommands {

    private static final Logger log = LoggerFactory.getLogger(ThreadCommands.class);
    private static final String GROUP = "Thread Commands";

    // ── ANSI colours ────────────────────────────────────────────────────────
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String WHITE  = "\u001B[37m";

    private final ThreadService threadService;

    public ThreadCommands(ThreadService threadService) {
        this.threadService = threadService;
    }

    // ────────────────────────────────────────────────────────────────────────
    // threads list
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Lists all registered virtual threads from the database.
     *
     * <pre>vdd:> threads list</pre>
     */
    @Command(command = { "threads", "list" }, description = "List all registered threads in the database", group = GROUP)
    public void threadsList(CommandContext ctx) {
        PrintWriter out = ctx.getTerminal().writer();
        try {
            List<ThreadEntity> threads = threadService.getAllThreads();

            if (threads.isEmpty()) {
                out.println(GREEN + "  No threads found in database." + RESET);
                out.flush();
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(CYAN).append(BOLD)
                    .append(String.format("  %-6s  %-12s  %-35s  %-12s  %s\n",
                            "DB ID", "THREAD ID", "NAME", "STATE", "CARRIER"))
                    .append(RESET);
            sb.append(CYAN).append("  ").append("─".repeat(85)).append(RESET).append("\n");

            for (ThreadEntity t : threads) {
                String stateStr = t.getState() != null ? t.getState().name() : "-";
                sb.append(String.format("  %-6d  %-12d  %-35s  %-12s  %s\n",
                        t.getId(),
                        t.getThreadId(),
                        truncate(t.getThreadName(), 35),
                        colorState(stateStr),
                        t.getCarrierThread() != null ? t.getCarrierThread() : "-"));
            }

            sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(threads.size()).append("\n");
            out.println(sb);
            out.flush();
        } catch (DataAccessException e) {
            log.error("threads list command failed: database access error", e);
            printError(out, "Unable to list threads — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // threads get
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets details of a single thread by its JVM thread-id.
     *
     * <pre>vdd:> threads get --thread-id 10023</pre>
     */
    @Command(command = { "threads", "get" }, description = "Get a thread by JVM thread-id", group = GROUP)
    public void threadsGet(
            CommandContext ctx,
            @Option(shortNames = 't', longNames = "thread-id", description = "JVM thread ID (e.g. 10023)") Long threadId) {

        PrintWriter out = ctx.getTerminal().writer();

        if (threadId == null) {
            printError(out, "Missing required option: --thread-id (-t)");
            return;
        }

        try {
            Optional<ThreadEntity> opt = threadService.getThreadByThreadId(threadId);
            if (opt.isEmpty()) {
                out.println(RED + "  Thread with threadId=" + threadId + " not found." + RESET);
                out.flush();
                return;
            }

            ThreadEntity t = opt.get();
            String details = "\n" + CYAN + BOLD + "  Thread Details\n" + RESET
                    + CYAN + "  " + "─".repeat(40) + RESET + "\n"
                    + WHITE + "  DB ID       : " + RESET + t.getId() + "\n"
                    + WHITE + "  Thread ID   : " + RESET + t.getThreadId() + "\n"
                    + WHITE + "  Name        : " + RESET + t.getThreadName() + "\n"
                    + WHITE + "  State       : " + RESET
                    + colorState(t.getState() != null ? t.getState().name() : "-") + "\n"
                    + WHITE + "  Carrier     : " + RESET
                    + (t.getCarrierThread() != null ? t.getCarrierThread() : "-") + "\n"
                    + WHITE + "  Created At  : " + RESET
                    + (t.getCreatedAt() != null ? t.getCreatedAt() : "-") + "\n"
                    + WHITE + "  Owned Locks : " + RESET
                    + (t.getOwnedLocks() != null ? t.getOwnedLocks().size() : 0) + "\n";
            out.println(details);
            out.flush();
        } catch (DataAccessException e) {
            log.error("threads get command failed: database access error", e);
            printError(out, "Unable to fetch thread — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // threads delete
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a thread by its database primary key.
     *
     * <pre>vdd:> threads delete --id 5</pre>
     */
    @Command(command = { "threads", "delete" }, description = "Delete a thread by its database ID", group = GROUP)
    public void threadsDelete(
            CommandContext ctx,
            @Option(shortNames = 'i', longNames = "id", description = "Database primary key of the thread") Long id) {

        PrintWriter out = ctx.getTerminal().writer();

        if (id == null) {
            printError(out, "Missing required option: --id (-i)");
            return;
        }

        try {
            threadService.deleteThread(id);
            out.println(GREEN + BOLD + "\n  ✔  Thread with DB id=" + id + " deleted.\n" + RESET);
            out.flush();
        } catch (EmptyResultDataAccessException e) {
            printError(out, "No thread found with DB id=" + id + " — nothing was deleted.");
        } catch (DataAccessException e) {
            log.error("threads delete command failed: database access error", e);
            printError(out, "Unable to delete thread — database is unreachable or write failed.");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String colorState(String state) {
        return switch (state) {
            case "BLOCKED" -> RED + BOLD + state + RESET;
            case "WAITING", "TIMED_WAITING" -> YELLOW + BOLD + state + RESET;
            case "RUNNABLE" -> GREEN + state + RESET;
            default -> WHITE + state + RESET;
        };
    }

    private void printError(PrintWriter out, String message) {
        out.println(RED + BOLD + "  ✘  " + message + RESET);
        out.flush();
    }
}