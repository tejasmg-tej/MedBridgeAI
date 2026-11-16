package com.medbridge.dto;

import java.time.LocalDateTime;

public record ChecklistItemDto(Long id, String category, String description, boolean completed, LocalDateTime completedAt) {
}
