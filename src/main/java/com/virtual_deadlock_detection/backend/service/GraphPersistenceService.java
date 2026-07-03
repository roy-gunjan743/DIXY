package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.DeadlockNodeEntity;
import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.mapper.GraphEntityMapper;
import com.virtual_deadlock_detection.backend.repository.DeadlockReportRepository;
import com.virtual_deadlock_detection.graph.model.DeadlockCycle;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphPersistenceService {

    private final DeadlockReportRepository reportRepository;
    private final GraphEntityMapper mapper;

    public GraphPersistenceService(
            DeadlockReportRepository reportRepository,
            GraphEntityMapper mapper) {

        this.reportRepository = reportRepository;
        this.mapper = mapper;
    }

    public DeadlockReportEntity saveCycle(DeadlockCycle cycle) {

        DeadlockReportEntity report = new DeadlockReportEntity();

        report.setSeverity("HIGH");
        report.setResolved(false);
        report.setAnalysisTimeMicros(0L);

        List<DeadlockNodeEntity> nodes = new ArrayList<>();

        int order = 1;

        for (GraphEdge edge : cycle.getEdgesInCycle()) {

            GraphNode graphNode = edge.getSource();

            ThreadEntity thread = mapper.getThread(graphNode);

            if (thread == null) {
                continue;
            }

            DeadlockNodeEntity node = new DeadlockNodeEntity();

            node.setReport(report);
            node.setThread(thread);
            node.setOrderIndex(order++);

            nodes.add(node);
        }

        report.setNodes(nodes);

        return reportRepository.save(report);
    }
}