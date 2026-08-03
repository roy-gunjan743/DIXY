package com.virtual_deadlock_detection.LockDetectionAgent.collector;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.sender.EventSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventCollector {

    private static final Logger log = LoggerFactory.getLogger(EventCollector.class);
    private final EventSender sender = new EventSender();

    public void collect(EventDTO eventDTO) {
        log.debug("Event Collected - Type: {}, Thread: {}", eventDTO.getEventType(), eventDTO.getThreadName());
        sender.send(eventDTO);
    }
}