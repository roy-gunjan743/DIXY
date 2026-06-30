package com.virtual_deadlock_detection.agent;

import java.lang.instrument.Instrumentation;

public class VirtualDeadlockAgent {

    public static void premain(String agentArgs, Instrumentation inst) {

        System.out.println("======================================");
        System.out.println(" Virtual Deadlock Detection Agent");
        System.out.println(" Agent Started Successfully");
        System.out.println("======================================");

        System.out.println("Loaded Classes : " + inst.getAllLoadedClasses().length);

    }

}