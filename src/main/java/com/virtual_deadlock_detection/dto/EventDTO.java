package com.virtual_deadlock_detection.dto;

import java.time.LocalDateTime;

public class EventDTO {

    private String eventType;
    private String threadName;
    private long threadId;
    private LocalDateTime timestamp;

    public EventDTO() {
    }

    public EventDTO(String eventType,
                    String threadName,
                    long threadId,
                    LocalDateTime timestamp) {

        this.eventType = eventType;
        this.threadName = threadName;
        this.threadId = threadId;
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
