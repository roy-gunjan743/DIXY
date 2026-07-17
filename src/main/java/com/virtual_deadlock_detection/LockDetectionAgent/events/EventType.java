package com.virtual_deadlock_detection.LockDetectionAgent.events;

public enum EventType {

    THREAD_START,
    THREAD_END,
    THREAD_BLOCKED,
    THREAD_PARKED,
    LOCK_ACQUIRE,
    LOCK_RELEASE,
    LOCK_OWNED
}