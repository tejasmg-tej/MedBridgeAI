package com.medbridge.service;

import com.medbridge.ai.MedicalDisclaimer;
import com.medbridge.dto.*;
import com.medbridge.entity.ChecklistItem;
import com.medbridge.entity.FollowUp;
import com.medbridge.entity.Medication;
import com.medbridge.entity.Report;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportMapper {

    public static int percent(int done, int total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    private int completedCount(Report r) {
        return (int) r.getChecklistItems().stream().filter(ChecklistItem::isCompleted).count();
    }

    public ReportSummaryDto toSummary(Report r) {
        int total = r.getChecklistItems().size();
        int done = completedCount(r);
        return new ReportSummaryDto(r.getId(), r.getFileName(), r.getPatientLabel(), r.getStatus().name(),
                r.getUploadedAt(), r.getAnalyzedAt(), r.getMedications().size(), total, done, percent(done, total));
    }

    public ReportDetailDto toDetail(Report r) {
        int total = r.getChecklistItems().size();
        int done = completedCount(r);
        List<MedicationDto> meds = r.getMedications().stream().map(this::toDto).toList();
        List<FollowUpDto> follow = r.getFollowUps().stream().map(this::toDto).toList();
        List<ChecklistItemDto> checklist = r.getChecklistItems().stream().map(this::toDto).toList();
        return new ReportDetailDto(r.getId(), r.getFileName(), r.getPatientLabel(), r.getStatus().name(),
                r.getErrorMessage(), r.getSimplifiedSummary(), r.getDiagnosisAsStated(), r.getHospitalStaySummary(),
                meds, follow, List.copyOf(r.getPrecautions()), List.copyOf(r.getMissingInformation()),
                checklist, total, done, percent(done, total), MedicalDisclaimer.TEXT,
                r.getUploadedAt(), r.getAnalyzedAt());
    }

    public MedicationDto toDto(Medication m) {
        return new MedicationDto(m.getId(), m.getName(), m.getDosage(), m.getFrequency(), m.getDuration(), m.getInstructions());
    }

    public FollowUpDto toDto(FollowUp f) {
        return new FollowUpDto(f.getId(), f.getProvider(), f.getPurpose(), f.getAppointmentDate(), f.getDetails());
    }

    public ChecklistItemDto toDto(ChecklistItem c) {
        return new ChecklistItemDto(c.getId(), c.getCategory().name(), c.getDescription(), c.isCompleted(), c.getCompletedAt());
    }
}
