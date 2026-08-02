package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.service.ThreadService;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.transaction.annotation.Transactional;

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
 */
@ShellComponent
@Transactional
public class ThreadCommands {


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
    @ShellMethod(value = "List all registered threads in the database", key = "threads list")
    public String threadsList() {
        List<ThreadEntity> threads = threadService.getAllThreads();

        if (threads.isEmpty()) {
            return GREEN + "  No threads found in database." + RESET;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD)
          .append(String.format("  %-6s  %-12s  %-35s  %-12s  %s\n",
                  "DB ID", "THREAD ID", "NAME", "STATE", "CARRIER"))
          .append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(85)).append(RESET).append("\n");

        for (ThreadEntity t : threads) {
            String stateStr = t.getState() != null ? t.getState().name() : "-";
            String stateColored;
            switch (stateStr) {
                case "BLOCKED":       stateColored = RED    + BOLD + stateStr + RESET; break;
                case "WAITING":
                case "TIMED_WAITING": stateColored = YELLOW + BOLD + stateStr + RESET; break;
                case "RUNNABLE":      stateColored = GREEN  + stateStr + RESET;        break;
                default:              stateColored = WHITE  + stateStr + RESET;
            }

            sb.append(String.format("  %-6d  %-12d  %-35s  %-12s  %s\n",
                    t.getId(),
                    t.getThreadId(),
                    truncate(t.getThreadName(), 35),
                    stateColored,
                    t.getCarrierThread() != null ? t.getCarrierThread() : "-"));
        }

        sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(threads.size()).append("\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // threads get
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets details of a single thread by its JVM thread-id.
     *
     * <pre>vdd:> threads get --thread-id 10023</pre>
     */
    @ShellMethod(value = "Get a thread by JVM thread-id", key = "threads get")
    public String threadsGet(
            @ShellOption(value = {"--thread-id", "-t"}, help = "JVM thread ID (e.g. 10023)") Long threadId) {

        Optional<ThreadEntity> opt = threadService.getThreadByThreadId(threadId);
        if (opt.isEmpty()) {
            return RED + "  Thread with threadId=" + threadId + " not found." + RESET;
        }

        ThreadEntity t = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD).append("  Thread Details\n").append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(40)).append(RESET).append("\n");
        sb.append(WHITE).append("  DB ID       : ").append(RESET).append(t.getId()).append("\n");
        sb.append(WHITE).append("  Thread ID   : ").append(RESET).append(t.getThreadId()).append("\n");
        sb.append(WHITE).append("  Name        : ").append(RESET).append(t.getThreadName()).append("\n");
        sb.append(WHITE).append("  State       : ").append(RESET)
          .append(colorState(t.getState() != null ? t.getState().name() : "-")).append("\n");
        sb.append(WHITE).append("  Carrier     : ").append(RESET)
          .append(t.getCarrierThread() != null ? t.getCarrierThread() : "-").append("\n");
        sb.append(WHITE).append("  Created At  : ").append(RESET)
          .append(t.getCreatedAt() != null ? t.getCreatedAt() : "-").append("\n");
        sb.append(WHITE).append("  Owned Locks : ").append(RESET)
          .append(t.getOwnedLocks() != null ? t.getOwnedLocks().size() : 0).append("\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // threads delete
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a thread by its database primary key.
     *
     * <pre>vdd:> threads delete --id 5</pre>
     */
    @ShellMethod(value = "Delete a thread by its database ID", key = "threads delete")
    public String threadsDelete(
            @ShellOption(value = {"--id", "-i"}, help = "Database primary key of the thread") Long id) {
        threadService.deleteThread(id);
        return GREEN + BOLD + "\n  ✔  Thread with DB id=" + id + " deleted.\n" + RESET;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String colorState(String state) {
        switch (state) {
            case "BLOCKED":       return RED    + BOLD + state + RESET;
            case "WAITING":
            case "TIMED_WAITING": return YELLOW + BOLD + state + RESET;
            case "RUNNABLE":      return GREEN  + state + RESET;
            default:              return WHITE  + state + RESET;
        }
    }
}
