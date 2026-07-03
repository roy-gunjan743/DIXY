package com.virtual_deadlock_detection.jfr;

import com.virtual_deadlock_detection.collector.EventCollector;
import com.virtual_deadlock_detection.dto.EventDTO;
import jdk.jfr.consumer.RecordingStream;

import java.time.LocalDateTime;

public class JfrEventListener {

    private final EventCollector collector = new EventCollector();

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");
        stream.enable("jdk.ThreadEnd");
        stream.enable("jdk.JavaMonitorEnter");
        stream.enable("jdk.JavaMonitorWait");
        stream.enable("jdk.ThreadPark");

        // Thread Start
        stream.onEvent("jdk.ThreadStart", event -> {

            EventDTO dto = new EventDTO();

            dto.setEventType(com.virtual_deadlock_detection.events.EventType.THREAD_START);
            dto.setThreadId(-1);
            dto.setThreadName("Unknown");
            dto.setTimestamp(LocalDateTime.now());

            collector.collect(dto);

            System.out.println("--------------------------------");
            System.out.println("Thread Started");
            System.out.println(event);

        });

    }
}

