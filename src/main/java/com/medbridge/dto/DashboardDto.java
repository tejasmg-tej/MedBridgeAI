package com.medbridge.dto;

import java.util.List;

public record DashboardDto(long totalReports, long analyzedReports, long failedReports, long totalMedications,
                           long totalChecklistItems, long completedChecklistItems, int overallProgressPercent,
                           List<ReportSummaryDto> recentReports) {
}
