package com.virtual_deadlock_detection.jvmti;

public class NativeLoader {

    public void loadLibrary() {

        System.out.println("----------------------------------");
        System.out.println("Preparing Native Library");
        System.out.println("----------------------------------");


        System.out.println("Library Name : "
                + JVMTIConstants.LIBRARY_NAME);

    }

}
