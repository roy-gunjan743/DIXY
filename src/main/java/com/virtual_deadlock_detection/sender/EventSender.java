package com.virtual_deadlock_detection.sender;

import com.virtual_deadlock_detection.dto.EventDTO;

public class EventSender {

    public void send(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Sending Event to Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name: " + eventDTO.getThreadName());
        System.out.println("--------------------------------");

    }

}
