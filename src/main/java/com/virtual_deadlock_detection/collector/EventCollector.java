package com.virtual_deadlock_detection.collector;

import com.virtual_deadlock_detection.dto.EventDTO;

public class EventCollector {

    public void collect(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Event Collected");
        System.out.println(eventDTO.getEventType());
        System.out.println(eventDTO.getThreadName());

    }

}
