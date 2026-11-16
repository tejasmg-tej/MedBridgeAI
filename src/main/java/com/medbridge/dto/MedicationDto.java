package com.medbridge.dto;

public record MedicationDto(Long id, String name, String dosage, String frequency, String duration, String instructions) {
}
