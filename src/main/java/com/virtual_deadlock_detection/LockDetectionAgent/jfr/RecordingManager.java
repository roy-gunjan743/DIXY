package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import com.virtual_deadlock_detection.LockDetectionAgent.collector.EventCollector;

import java.util.Objects;

public class RecordingManager {

    private final JfrRecorder recorder;

    private EventCollector collector;

    public RecordingManager() {
        this.recorder = new JfrRecorder();
    }

    // New — this is the "setCollector" the commented-out call in
    // VirtualDeadlockAgent was waiting for.
    public void setCollector(EventCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    public void startRecording() {

        if (collector == null) {
            throw new IllegalStateException(
                    "RecordingManager.setCollector(...) must be called before startRecording()"
            );
        }

        recorder.start(collector);
    }

    public void stopRecording() {
        recorder.stop();
    }
}