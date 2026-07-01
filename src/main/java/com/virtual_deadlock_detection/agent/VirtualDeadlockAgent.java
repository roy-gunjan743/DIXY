package com.virtual_deadlock_detection.agent;

import com.virtual_deadlock_detection.jfr.RecordingManager;
import com.virtual_deadlock_detection.jvmti.JvmtiBridge;

import java.lang.instrument.Instrumentation;
import com.virtual_deadlock_detection.jfr.RecordingManager;

public class VirtualDeadlockAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        System.out.println("======================================");
        System.out.println(" Virtual Deadlock Detection Agent");
        System.out.println(" Agent Started Successfully");
        System.out.println("======================================");

        System.out.println("Loaded Classes : " + inst.getAllLoadedClasses().length);
        RecordingManager recordingManager = new RecordingManager();
        recordingManager.startRecording();
        JvmtiBridge jvmtiBridge = new JvmtiBridge();
        jvmtiBridge.initialize();

    }

}