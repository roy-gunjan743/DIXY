package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.dto.DeadlockReportDTO;
import com.virtual_deadlock_detection.backend.entity.DeadlockNodeEntity;
import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.repository.DeadlockReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeadlockReportService {

    private final DeadlockReportRepository repository;

    public DeadlockReportService(DeadlockReportRepository repository) {
        this.repository = repository;
    }

    public List<DeadlockReportDTO> getAllReports() {

        return repository.findAll()
                .stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    public DeadlockReportDTO getReport(Long id) {

        return repository.findById(id)
                .map(this::convert)
                .orElse(null);
    }

    private DeadlockReportDTO convert(DeadlockReportEntity entity) {

        List<String> threads = entity.getNodes()
                .stream()
                .map(DeadlockNodeEntity::getThread)
                .map(thread -> thread.getThreadName())
                .collect(Collectors.toList());

        return new DeadlockReportDTO(
                entity.getId(),
                entity.getSeverity(),
                entity.isResolved(),
                entity.getAnalysisTimeMicros(),
                entity.getDetectedAt(),
                threads,
                entity.getAppName(),
                entity.getInstanceId(),
                entity.getDetectionStrategy()
        );
    }
}