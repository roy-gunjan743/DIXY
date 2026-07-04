package com.virtual_deadlock_detection.collector;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.sender.EventSender;

public class EventCollector {

    private final EventSender sender = new EventSender();

    public void collect(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Event Collected");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread     : " + eventDTO.getThreadName());
        System.out.println("--------------------------------");

        sender.send(eventDTO);
    }
}