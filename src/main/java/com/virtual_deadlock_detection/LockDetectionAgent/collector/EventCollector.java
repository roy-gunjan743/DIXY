package com.virtual_deadlock_detection.LockDetectionAgent.collector;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.sender.EventSender;
import com.virtual_deadlock_detection.LockDetectionAgent.logging.Logger;

public class EventCollector {

    private static final Logger log = Logger.getLogger(EventCollector.class);
    private final EventSender sender = new EventSender();

    public void collect(EventDTO eventDTO) {
        log.debug("Event Collected - Type: {}, Thread: {}", eventDTO.getEventType(), eventDTO.getThreadName());
        sender.send(eventDTO);
    }
}