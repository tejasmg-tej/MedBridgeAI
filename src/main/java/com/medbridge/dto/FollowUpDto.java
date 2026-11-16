package com.medbridge.dto;

public record FollowUpDto(Long id, String provider, String purpose, String appointmentDate, String details) {
}
