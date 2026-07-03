package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.DeadlockReportEntity;
import com.virtual_deadlock_detection.backend.repository.DeadlockReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeadlockService {

    private final DeadlockReportRepository deadlockReportRepository;

    public DeadlockService(DeadlockReportRepository deadlockReportRepository) {
        this.deadlockReportRepository = deadlockReportRepository;
    }

    public DeadlockReportEntity saveReport(DeadlockReportEntity report) {
        return deadlockReportRepository.save(report);
    }

    public List<DeadlockReportEntity> getAllReports() {
        return deadlockReportRepository.findAll();
    }

    public Optional<DeadlockReportEntity> getReport(Long id) {
        return deadlockReportRepository.findById(id);
    }

    public void deleteReport(Long id) {
        deadlockReportRepository.deleteById(id);
    }
}