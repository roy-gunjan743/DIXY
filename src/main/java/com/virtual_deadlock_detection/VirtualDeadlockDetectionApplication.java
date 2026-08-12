package com.virtual_deadlock_detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
// Spring Shell 4: @CommandScan was removed — with Spring Boot, @Command
// beans under com.virtual_deadlock_detection.cli are now discovered
// automatically. See DeadlockCommands/LockCommands/ThreadCommands.
public class VirtualDeadlockDetectionApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(VirtualDeadlockDetectionApplication.class, args);
    }

}