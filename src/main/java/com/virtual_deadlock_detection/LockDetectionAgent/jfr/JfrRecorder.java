package com.virtual_deadlock_detection.LockDetectionAgent.jfr;

import jdk.jfr.consumer.RecordingStream;

public class JfrRecorder {

    private RecordingStream recordingStream;

    public void start() {

        recordingStream = new RecordingStream();

        JfrEventListener listener = new JfrEventListener();

        listener.registerEvents(recordingStream);

        recordingStream.startAsync();

        System.out.println("JFR Recording Started.");

    }

    public void stop() {

        if(recordingStream != null){

            recordingStream.close();

        }

    }

}
