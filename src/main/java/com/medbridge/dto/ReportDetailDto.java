package com.medbridge.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDetailDto(Long id, String fileName, String patientLabel, String status, String errorMessage,
                              String simplifiedSummary, String diagnosisAsStated, String hospitalStaySummary,
                              List<MedicationDto> medications, List<FollowUpDto> followUps,
                              List<String> precautions, List<String> missingInformation,
                              List<ChecklistItemDto> checklist, int checklistTotal, int checklistCompleted,
                              int progressPercent, String disclaimer,
                              LocalDateTime uploadedAt, LocalDateTime analyzedAt) {
}
