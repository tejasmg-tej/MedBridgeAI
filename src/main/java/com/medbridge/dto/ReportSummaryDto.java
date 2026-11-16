package com.medbridge.dto;

import java.time.LocalDateTime;

public record ReportSummaryDto(Long id, String fileName, String patientLabel, String status,
                               LocalDateTime uploadedAt, LocalDateTime analyzedAt, int medicationCount,
                               int checklistTotal, int checklistCompleted, int progressPercent) {
}
