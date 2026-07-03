package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.dto.AgentDeadlockDTO;
import com.virtual_deadlock_detection.backend.entity.DeadlockNodeEntity;
import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.repository.DeadlockReportRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentDeadlockService {

    private final DeadlockReportRepository reportRepository;
    private final ThreadRepository threadRepository;

    public AgentDeadlockService(
            DeadlockReportRepository reportRepository,
            ThreadRepository threadRepository) {

        this.reportRepository = reportRepository;
        this.threadRepository = threadRepository;
    }

    public void save(AgentDeadlockDTO dto) {

        DeadlockReportEntity report = new DeadlockReportEntity();

        report.setSeverity(dto.getSeverity());
        report.setResolved(false);
        report.setAnalysisTimeMicros(dto.getAnalysisTimeMicros());

        List<DeadlockNodeEntity> nodes = new ArrayList<>();

        int order = 1;

        for (Long threadId : dto.getThreadIds()) {

            ThreadEntity thread =
                    threadRepository.findByThreadId(threadId)
                            .orElse(null);

            if (thread == null)
                continue;

            DeadlockNodeEntity node = new DeadlockNodeEntity();

            node.setReport(report);
            node.setThread(thread);
            node.setOrderIndex(order++);

            nodes.add(node);
        }

        report.setNodes(nodes);

        reportRepository.save(report);
    }
}