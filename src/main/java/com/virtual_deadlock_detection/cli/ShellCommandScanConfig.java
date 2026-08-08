package com.virtual_deadlock_detection.cli;

import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.command.annotation.EnableCommand;

/**
 * Explicit Shell command scan and registration configuration.
 */
@Configuration
@CommandScan("com.virtual_deadlock_detection.cli")
@EnableCommand({DeadlockCommands.class, ThreadCommands.class, LockCommands.class})
public class ShellCommandScanConfig {
}
