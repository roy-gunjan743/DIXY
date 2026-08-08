package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.service.LockService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
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
 *
 * <p>Built on the Spring Shell 4.0 command model ({@code @Command} / {@code @Option}
 * from {@code org.springframework.shell.core.command.annotation}). Output goes
 * through {@code ctx.outputWriter()} rather than raw {@code System.out} — the
 * 4.x {@link CommandContext} exposes the writer directly — and database errors
 * are caught and reported as a clean one-line message instead of a raw stack
 * trace surfacing in the shell.</p>
 */
@Component
@Transactional
public class LockCommands {

    private static final Logger log = LoggerFactory.getLogger(LockCommands.class);
    private static final String GROUP = "Lock Commands";

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
    @Command(name = { "locks", "list" }, description = "List all locks in the database", group = GROUP)
    public void locksList(CommandContext ctx) {
        PrintWriter out = ctx.outputWriter();
        try {
            List<LockEntity> locks = lockService.getAllLocks();

            if (locks.isEmpty()) {
                out.println(GREEN + "  No locks found in database." + RESET);
                out.flush();
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(CYAN).append(BOLD)
                    .append(String.format("  %-6s  %-30s  %-22s  %s\n",
                            "DB ID", "LOCK ID", "TYPE", "OWNER THREAD"))
                    .append(RESET);
            sb.append(CYAN).append("  ").append("─".repeat(80)).append(RESET).append("\n");

            for (LockEntity l : locks) {
                String typeStr   = l.getLockType() != null ? l.getLockType() : "-";
                String typePadded = String.format("%-22s", typeStr);
                String typeColor = typeStr.toLowerCase().contains("synchronized")
                        ? YELLOW + BOLD + typePadded + RESET
                        : WHITE + typePadded + RESET;

                String owner = "-";
                if (l.getOwnerThread() != null) {
                    owner = l.getOwnerThread().getThreadName()
                            + " (id=" + l.getOwnerThread().getThreadId() + ")";
                }

                sb.append(String.format("  %-6d  %-30s  %s  %s\n",
                        l.getId(), truncate(l.getLockId(), 30), typeColor, owner));
            }

            sb.append("\n").append(WHITE).append("  Total: ").append(RESET).append(locks.size()).append("\n");
            out.println(sb);
            out.flush();
        } catch (DataAccessException e) {
            log.error("locks list command failed: database access error", e);
            printError(out, "Unable to list locks — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // locks get
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gets details of a specific lock by its lock-id string.
     *
     * <pre>vdd:> locks get --lock-id Monitor-2351897819504</pre>
     */
    @Command(name = { "locks", "get" }, description = "Get a lock by its lock-id string", group = GROUP)
    public void locksGet(
            CommandContext ctx,
            @Option(shortName = 'l', longName = "lock-id", description = "Lock ID string (e.g. Monitor-2351897819504)") String lockId) {

        PrintWriter out = ctx.outputWriter();

        if (lockId == null || lockId.isBlank()) {
            printError(out, "Missing required option: --lock-id (-l)");
            return;
        }

        try {
            Optional<LockEntity> opt = lockService.getLockByLockId(lockId);
            if (opt.isEmpty()) {
                out.println(RED + "  Lock with lockId='" + lockId + "' not found." + RESET);
                out.flush();
                return;
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

            out.println(sb);
            out.flush();
        } catch (DataAccessException e) {
            log.error("locks get command failed: database access error", e);
            printError(out, "Unable to fetch lock — database is unreachable or query failed.");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // locks delete
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a lock by its database primary key.
     *
     * <pre>vdd:> locks delete --id 3</pre>
     */
    @Command(name = { "locks", "delete" }, description = "Delete a lock by its database ID", group = GROUP)
    public void locksDelete(
            CommandContext ctx,
            @Option(shortName = 'i', longName = "id", description = "Database primary key of the lock") Long id) {

        PrintWriter out = ctx.outputWriter();

        if (id == null) {
            printError(out, "Missing required option: --id (-i)");
            return;
        }

        try {
            lockService.deleteLock(id);
            out.println(GREEN + BOLD + "\n  ✔  Lock with DB id=" + id + " deleted.\n" + RESET);
            out.flush();
        } catch (EmptyResultDataAccessException e) {
            printError(out, "No lock found with DB id=" + id + " — nothing was deleted.");
        } catch (DataAccessException e) {
            log.error("locks delete command failed: database access error", e);
            printError(out, "Unable to delete lock — database is unreachable or write failed.");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private void printError(PrintWriter out, String message) {
        out.println(RED + BOLD + "  ✘  " + message + RESET);
        out.flush();
    }
}