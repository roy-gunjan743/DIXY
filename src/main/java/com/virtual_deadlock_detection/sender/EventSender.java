package com.virtual_deadlock_detection.sender;

import com.virtual_deadlock_detection.dto.EventDTO;
import com.virtual_deadlock_detection.integration.GraphGateway;

public class EventSender {
    private final GraphGateway graphGateway = new GraphGateway();

    public void send(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Sending Event to Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name: " + eventDTO.getThreadName());
        System.out.println("--------------------------------");
        graphGateway.sendToGraph(eventDTO);

    }

}
