package com.medbridge.service;

import com.medbridge.dto.DashboardDto;
import com.medbridge.dto.ReportSummaryDto;
import com.medbridge.entity.ReportStatus;
import com.medbridge.repository.ChecklistItemRepository;
import com.medbridge.repository.MedicationRepository;
import com.medbridge.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final ReportRepository reportRepository;
    private final MedicationRepository medicationRepository;
    private final ChecklistItemRepository checklistRepository;
    private final ReportService reportService;

    public DashboardService(ReportRepository reportRepository, MedicationRepository medicationRepository,
                            ChecklistItemRepository checklistRepository, ReportService reportService) {
        this.reportRepository = reportRepository;
        this.medicationRepository = medicationRepository;
        this.checklistRepository = checklistRepository;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public DashboardDto build() {
        long totalItems = checklistRepository.count();
        long doneItems = checklistRepository.countByCompletedTrue();
        int progress = totalItems == 0 ? 0 : (int) Math.round(doneItems * 100.0 / totalItems);
        List<ReportSummaryDto> recent = reportService.list().stream().limit(5).toList();
        return new DashboardDto(
                reportRepository.count(),
                reportRepository.countByStatus(ReportStatus.ANALYZED),
                reportRepository.countByStatus(ReportStatus.FAILED),
                medicationRepository.count(),
                totalItems, doneItems, progress, recent);
    }
}
