package com.virtual_deadlock_detection.jfr;

import com.virtual_deadlock_detection.collector.EventCollector;
import com.virtual_deadlock_detection.dto.EventDTO;
import jdk.jfr.consumer.RecordingStream;

import java.time.LocalDateTime;

public class JfrEventListener {

    private final EventCollector collector = new EventCollector();

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");

        stream.onEvent("jdk.ThreadStart", event -> {

            EventDTO dto = new EventDTO();

            dto.setEventType("ThreadStart");
            dto.setTimestamp(LocalDateTime.now());

            // Temporary values until we integrate real thread metadata
            dto.setThreadName("Unknown");
            dto.setThreadId(-1);

            collector.collect(dto);

            System.out.println("--------------------------------");
            System.out.println("New Thread Started");
            System.out.println(event);

        });

    }
}


