package com.virtual_deadlock_detection.jvmti;

public class JvmtiBridge {

    private final NativeLoader nativeLoader;

    public JvmtiBridge() {

        nativeLoader = new NativeLoader();

    }

    public void initialize() {

        nativeLoader.loadLibrary();

    }

}
