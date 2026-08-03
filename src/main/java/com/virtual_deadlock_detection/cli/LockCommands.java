package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.service.LockService;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CLI commands for inspecting and managing locks stored in the database.
 *
 * <pre>
 *   vdd:> locks list
 *   vdd:> locks get --lock-id Monitor-2351897819504
 *   vdd:> locks delete --id 3
 * </pre>
 */
@ShellComponent
@Transactional
public class LockCommands {


    // ── ANSI colours ────────────────────────────────────────────────────────
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String WHITE  = "\u001B[37m";

    private final LockService lockService;

    public LockCommands(LockService lockService) {
        this.lockService = lockService;
    }

    // ────────────────────────────────────────────────────────────────────────
    // locks list
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Lists all locks currently held in the database.
     *
     * <pre>vdd:> locks list</pre>
     */
    @ShellMethod(value = "List all locks in the database", key = "locks list")
    public String locksList() {
        List<LockEntity> locks = lockService.getAllLocks();

        if (locks.isEmpty()) {
            return GREEN + "  No locks found in database." + RESET;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD)
          .append(String.format("  %-6s  %-30s  %-22s  %s\n",
                  "DB ID", "LOCK ID", "TYPE", "OWNER THREAD"))
          .append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(80)).append(RESET).append("\n");

        for (LockEntity l : locks) {
            String typeStr   = l.getLockType() != null ? l.getLockType() : "-";
            String typeColor = typeStr.toLowerCase().contains("synchronized")
                    ? YELLOW + BOLD + typeStr + RESET
                    : WHITE + typeStr + RESET;

            String owner = "-";
            if (l.getOwnerThread() != null) {
                owner = l.getOwnerThread().getThreadName()
                      + " (id=" + l.getOwnerThread().getThreadId() + ")";
            }

            sb.append(String.format("  %-6d  %-30s  %-22s  %s\n",
                    l.getId(),
                    truncate(l.getLockId(), 30),
                    typeColor,
                    owner));
        }

        sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(locks.size()).append("\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // locks get
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets details of a specific lock by its lock-id string.
     *
     * <pre>vdd:> locks get --lock-id Monitor-2351897819504</pre>
     */
    @ShellMethod(value = "Get a lock by its lock-id string", key = "locks get")
    public String locksGet(
            @ShellOption(value = {"--lock-id", "-l"}, help = "Lock ID string (e.g. Monitor-2351897819504)") String lockId) {

        Optional<LockEntity> opt = lockService.getLockByLockId(lockId);
        if (opt.isEmpty()) {
            return RED + "  Lock with lockId='" + lockId + "' not found." + RESET;
        }

        LockEntity l = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD).append("  Lock Details\n").append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(40)).append(RESET).append("\n");
        sb.append(WHITE).append("  DB ID       : ").append(RESET).append(l.getId()).append("\n");
        sb.append(WHITE).append("  Lock ID     : ").append(RESET).append(l.getLockId()).append("\n");
        sb.append(WHITE).append("  Type        : ").append(RESET)
          .append(l.getLockType() != null
                  ? (l.getLockType().toLowerCase().contains("synchronized")
                          ? YELLOW + BOLD + l.getLockType() + RESET
                          : l.getLockType())
                  : "-").append("\n");

        if (l.getOwnerThread() != null) {
            sb.append(WHITE).append("  Owner       : ").append(RESET)
              .append(l.getOwnerThread().getThreadName())
              .append(" (threadId=").append(l.getOwnerThread().getThreadId()).append(")").append("\n");
            sb.append(WHITE).append("  Owner State : ").append(RESET)
              .append(l.getOwnerThread().getState() != null ? l.getOwnerThread().getState().name() : "-").append("\n");
        } else {
            sb.append(WHITE).append("  Owner       : ").append(RESET).append("none\n");
        }

        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // locks delete
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a lock by its database primary key.
     *
     * <pre>vdd:> locks delete --id 3</pre>
     */
    @ShellMethod(value = "Delete a lock by its database ID", key = "locks delete")
    public String locksDelete(
            @ShellOption(value = {"--id", "-i"}, help = "Database primary key of the lock") Long id) {
        lockService.deleteLock(id);
        return GREEN + BOLD + "\n  ✔  Lock with DB id=" + id + " deleted.\n" + RESET;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
