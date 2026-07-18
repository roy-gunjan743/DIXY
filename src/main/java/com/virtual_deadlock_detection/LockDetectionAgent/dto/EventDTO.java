package com.virtual_deadlock_detection.LockDetectionAgent.dto;

import com.virtual_deadlock_detection.LockDetectionAgent.events.EventType;

import java.time.LocalDateTime;

public class EventDTO {

    private EventType eventType;

    private long threadId;

    private String threadName;

    private long lockAddress;

    // NEW
    private long ownerThreadId;

    private String ownerThreadName;

    private LocalDateTime timestamp;

    private long carrierThreadId;

    private String carrierThreadName;

    public long getCarrierThreadId() {
        return carrierThreadId;
    }

    public void setCarrierThreadId(long carrierThreadId) {
        this.carrierThreadId = carrierThreadId;
    }

    public String getCarrierThreadName() {
        return carrierThreadName;
    }

    public void setCarrierThreadName(String carrierThreadName) {
        this.carrierThreadName = carrierThreadName;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public long getLockAddress() {
        return lockAddress;
    }

    public void setLockAddress(long lockAddress) {
        this.lockAddress = lockAddress;
    }

    public long getOwnerThreadId() {
        return ownerThreadId;
    }

    public void setOwnerThreadId(long ownerThreadId) {
        this.ownerThreadId = ownerThreadId;
    }

    public String getOwnerThreadName() {
        return ownerThreadName;
    }

    public void setOwnerThreadName(String ownerThreadName) {
        this.ownerThreadName = ownerThreadName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}