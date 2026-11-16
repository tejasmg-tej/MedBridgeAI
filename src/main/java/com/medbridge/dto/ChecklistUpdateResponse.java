package com.medbridge.dto;

public record ChecklistUpdateResponse(ChecklistItemDto item, Long reportId, int checklistTotal, int checklistCompleted, int progressPercent) {
}
