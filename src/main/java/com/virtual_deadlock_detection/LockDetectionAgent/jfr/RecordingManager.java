package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

public class RecordingManager {

    private final JfrRecorder recorder;

    public RecordingManager() {
        this.recorder = new JfrRecorder();
    }

    public void startRecording() {
        recorder.start();
    }

    public void stopRecording() {
        recorder.stop();
    }
}
