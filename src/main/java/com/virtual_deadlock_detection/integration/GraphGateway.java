package com.virtual_deadlock_detection.integration;

import com.virtual_deadlock_detection.dto.EventDTO;

public class GraphGateway {

    public void sendToGraph(EventDTO eventDTO) {

        System.out.println("--------------------------------");
        System.out.println("Forwarding Event To Graph Engine");
        System.out.println("Event Type : " + eventDTO.getEventType());
        System.out.println("Thread Name: " + eventDTO.getThreadName());
        System.out.println("--------------------------------");

        /*
            Member 2 integration happens here.

            Example:

            waitForGraph.processEvent(eventDTO);

        */

    }

}