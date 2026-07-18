package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;
import jdk.jfr.consumer.RecordingStream;

import java.util.Objects;

public class JfrRecorder {

    private RecordingStream recordingStream;

    public void start(EventCollector collector) {

        Objects.requireNonNull(collector, "collector");

        recordingStream = new RecordingStream();

        JfrEventListener listener = new JfrEventListener(collector);

        listener.registerEvents(recordingStream);

        recordingStream.startAsync();

        System.out.println("JFR Recording Started.");
    }

    public void stop() {

        if (recordingStream != null) {

            recordingStream.close();
        }
    }
}