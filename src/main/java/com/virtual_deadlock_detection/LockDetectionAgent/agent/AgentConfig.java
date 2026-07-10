package com.virtual_deadlock_detection.LockDetectionAgent.agent;

public class AgentConfig {

    private boolean jfrEnabled = true;
    private boolean jvmtiEnabled = true;

    public boolean isJfrEnabled() {
        return jfrEnabled;
    }

    public boolean isJvmtiEnabled() {
        return jvmtiEnabled;
    }

}