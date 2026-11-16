package com.medbridge.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Structured JSON returned by Gemini (parsed and validated before anything is stored). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisResult(
        Boolean looksLikeDischargeSummary,
        String simplifiedSummary,
        String diagnosisAsStated,
        String hospitalStaySummary,
        List<MedicationItem> medications,
        List<FollowUpItem> followUps,
        List<String> precautions,
        List<String> missingInformation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MedicationItem(String name, String dosage, String frequency, String duration,
                                 String instructions, String sourceText) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FollowUpItem(String provider, String purpose, String appointmentDate, String details) {
    }
}
