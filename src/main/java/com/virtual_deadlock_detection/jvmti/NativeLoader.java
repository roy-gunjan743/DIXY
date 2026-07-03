package com.virtual_deadlock_detection.jvmti;

public class NativeLoader {

    public void loadLibrary() {

        System.out.println("----------------------------------");
        System.out.println("Loading JVMTI Native Library");
        System.out.println("----------------------------------");

        System.loadLibrary(
                JVMTIConstants.LIBRARY_NAME
        );

        System.out.println("Native Library Loaded");
    }
}