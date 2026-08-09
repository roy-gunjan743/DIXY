package com.virtual_deadlock_detection.cli;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.jvmti.NativeEventBridge;

import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CLI command for viewing recent raw JVMTI callback events
 * (LockOwned / LockReleased), newest first.
 *
 * <pre>vdd:> events list</pre>
 */
@Component
public class EventCommands {

    private static final String GROUP = "Event Commands";

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN  = "\u001B[32m";
    private static final String CYAN   = "\u001B[36m";
    private static final String WHITE  = "\u001B[37m";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Command(name = { "events", "list" }, description = "Show recent raw JVMTI callback events (LockOwned / LockReleased)", group = GROUP)
    public void eventsList(CommandContext ctx) {
        PrintWriter out = ctx.outputWriter();
        List<EventDTO> events = NativeEventBridge.getRecentEvents();

        if (events.isEmpty()) {
            out.println(GREEN + "  No JVMTI callback events captured yet." + RESET);
            out.flush();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(CYAN).append(BOLD)
                .append(String.format("  %-12s  %-14s  %-22s  %-22s  %s\n",
                        "TIME", "EVENT", "WAITING/THREAD", "OWNER", "LOCK"))
                .append(RESET);
        sb.append(CYAN).append("  ").append("─".repeat(85)).append(RESET).append("\n");

        for (EventDTO e : events) {
            String time = e.getTimestamp() != null ? e.getTimestamp().format(TIME_FMT) : "-";
            String type = e.getEventType() != null ? e.getEventType().name() : "-";
            String waiting = e.getThreadName() != null
                    ? e.getThreadName() + "(" + e.getThreadId() + ")"
                    : String.valueOf(e.getThreadId());
            String owner = (e.getOwnerThreadName() != null && !e.getOwnerThreadName().isEmpty())
                    ? e.getOwnerThreadName() + "(" + e.getOwnerThreadId() + ")"
                    : "-";
            String lock = e.getLockAddress() != 0 ? String.valueOf(e.getLockAddress()) : "-";

            sb.append(String.format("  %-12s  %s%-14s%s  %-22s  %-22s  %s\n",
                    time, YELLOW, type, RESET, waiting, owner, lock));
        }

        sb.append("\n").append(WHITE).append("  Total shown: ").append(RESET).append(events.size()).append("\n");
        out.println(sb);
        out.flush();
    }
}