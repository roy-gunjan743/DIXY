package com.virtual_deadlock_detection.jfr;

import jdk.jfr.consumer.RecordingStream;

public class JfrEventListener {

    public void registerEvents(RecordingStream stream) {

        stream.enable("jdk.ThreadStart");
        stream.onEvent("jdk.ThreadStart", event -> {
            System.out.println("--------------------------------");
            System.out.println("New Thread Started");
            System.out.println(event);
        });

    }
}


