package com.medbridge.dto;

import jakarta.validation.constraints.NotNull;

public record ChecklistToggleRequest(@NotNull(message = "must be true or false") Boolean completed) {
}
