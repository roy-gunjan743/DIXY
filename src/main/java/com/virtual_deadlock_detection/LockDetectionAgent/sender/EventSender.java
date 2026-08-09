package com.virtual_deadlock_detection.LockDetectionAgent.sender;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.integration.GraphGateway;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.RuntimeContext;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;
import com.virtual_deadlock_detection.LockDetectionAgent.logging.Logger;

public class EventSender {

    private static final Logger log = Logger.getLogger(EventSender.class);
    private final GraphGateway graphGateway =
            new GraphGateway(RuntimeContext.GRAPH);

    public void send(EventDTO eventDTO) {
        log.debug("Sending Event To Graph Engine - Type: {}, Thread: {}", eventDTO.getEventType(), eventDTO.getThreadName());
        graphGateway.processEvent(eventDTO);
    }

    public static WaitForGraph getGraph() {
        return RuntimeContext.GRAPH;
    }
}