package com.virtual_deadlock_detection.dto;

import com.virtual_deadlock_detection.events.EventType;

import java.time.LocalDateTime;

public class EventDTO {

    private EventType eventType;
    private String threadName;
    private long threadId;
    private LocalDateTime timestamp;

    // 1. ADD THIS NEW VARIABLE
    private long lockAddress;

    public EventDTO() {
    }

    public EventDTO(EventType eventType,
                    String threadName,
                    long threadId,
                    LocalDateTime timestamp) {

        this.eventType = eventType;
        this.threadName = threadName;
        this.threadId = threadId;
        this.timestamp = timestamp;
    }

    // --- Keep all your existing getters and setters here ---
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }
    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    // 2. ADD THESE TWO NEW METHODS AT THE VERY BOTTOM
    public long getLockAddress() {
        return lockAddress;
    }

    public void setLockAddress(long lockAddress) {
        this.lockAddress = lockAddress;
    }
}