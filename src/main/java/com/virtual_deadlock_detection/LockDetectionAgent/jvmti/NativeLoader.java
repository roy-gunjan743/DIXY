package com.virtual_deadlock_detection.LockDetectionAgent.jvmti;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLoader {

    public void loadLibrary() {

        System.out.println("----------------------------------");
        System.out.println("Loading JVMTI Native Library");
        System.out.println("----------------------------------");

        String os = System.getProperty("os.name").toLowerCase().contains("win") ? "Windows" : "Linux";
        String arch = System.getProperty("os.arch");
        String libName = os.equals("Windows") ? "virtual_deadlock_agent.dll" : "libvirtual_deadlock_agent.so";
        String resourcePath = "/native/" + os + "-" + arch + "/" + libName;

        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native lib not found at " + resourcePath);
            }
            Path tmp = Files.createTempFile("dixy-native-", "-" + libName);
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }

        System.out.println("Native Library Loaded");
    }
}