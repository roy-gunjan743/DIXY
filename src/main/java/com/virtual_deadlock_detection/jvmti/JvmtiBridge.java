package com.virtual_deadlock_detection.jvmti;

public class JvmtiBridge {

    private native void initializeAgent();
    private native void shutdownAgent();

    private final NativeLoader nativeLoader;

    public JvmtiBridge() {
        nativeLoader = new NativeLoader();
    }

    public void initialize() {

        nativeLoader.loadLibrary();

        System.out.println("JVMTI Bridge Initialized");

        initializeAgent();
    }

    public void shutdown() {

        System.out.println("JVMTI Bridge Shutdown");

        // shutdownAgent();
    }
}