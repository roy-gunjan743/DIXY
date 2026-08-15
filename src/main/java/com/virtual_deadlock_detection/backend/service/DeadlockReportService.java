package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.dto.DeadlockReportDTO;
import com.virtual_deadlock_detection.backend.entity.DeadlockNodeEntity;
import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.repository.DeadlockReportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeadlockReportService {

    private static final int MAX_REPORTS = 20;

    private final DeadlockReportRepository repository;

    public DeadlockReportService(DeadlockReportRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns only the latest deadlock reports.
     *
     * We intentionally limit this query because the database may contain
     * thousands of reports. Loading every report at once can cause:
     *
     * - slow CLI response
     * - excessive memory usage
     * - lazy-loading problems
     * - Hibernate session errors
     */
    @Transactional(readOnly = true)
    public List<DeadlockReportDTO> getAllReports() {

        return repository.findAll(
                        PageRequest.of(
                                0,
                                MAX_REPORTS,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "detectedAt"
                                )
                        )
                )
                .getContent()
                .stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single deadlock report.
     */
    @Transactional(readOnly = true)
    public DeadlockReportDTO getReport(Long id) {

        return repository.findById(id)
                .map(this::convert)
                .orElse(null);
    }

    /**
     * Converts the database entity into the DTO used by
     * the CLI / REST API.
     *
     * Because this method is called inside a read-only transaction,
     * the lazy nodes collection can be initialized safely.
     */
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

    /**
     * Clears all VDD telemetry data.
     *
     * Uses a PostgreSQL-level TRUNCATE instead of Hibernate
     * deleteAll/deleteAllInBatch because the database can contain
     * millions of telemetry rows.
     */
    @Transactional
    public void clearAllReports() {
        repository.truncateAllTelemetry();
    }
}