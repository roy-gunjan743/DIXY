package com.virtual_deadlock_detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

import java.util.TimeZone;

@SpringBootApplication
@CommandScan("com.virtual_deadlock_detection.cli")// required for Spring Shell 3.x's @Command annotation model to be discovered — see DeadlockCommands
public class VirtualDeadlockDetectionApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(VirtualDeadlockDetectionApplication.class, args);
    }

}