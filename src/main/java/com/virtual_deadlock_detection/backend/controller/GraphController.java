package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.service.GraphService;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @PostMapping("/detect")
    public List<DeadlockCycle> detectDeadlocks() {
        return graphService.detectDeadlocks();
    }
}