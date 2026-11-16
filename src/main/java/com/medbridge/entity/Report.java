package com.medbridge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "patient_label", length = 100)
    private String patientLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.UPLOADED;

    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(name = "simplified_summary", columnDefinition = "TEXT")
    private String simplifiedSummary;

    @Column(name = "diagnosis_as_stated", columnDefinition = "TEXT")
    private String diagnosisAsStated;

    @Column(name = "hospital_stay_summary", columnDefinition = "TEXT")
    private String hospitalStaySummary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Medication> medications = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FollowUp> followUps = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ChecklistItem> checklistItems = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "report_precautions", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "precaution", length = 1000)
    private List<String> precautions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "report_missing_info", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "item", length = 1000)
    private List<String> missingInformation = new ArrayList<>();

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getPatientLabel() { return patientLabel; }
    public void setPatientLabel(String patientLabel) { this.patientLabel = patientLabel; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public String getSimplifiedSummary() { return simplifiedSummary; }
    public void setSimplifiedSummary(String simplifiedSummary) { this.simplifiedSummary = simplifiedSummary; }
    public String getDiagnosisAsStated() { return diagnosisAsStated; }
    public void setDiagnosisAsStated(String diagnosisAsStated) { this.diagnosisAsStated = diagnosisAsStated; }
    public String getHospitalStaySummary() { return hospitalStaySummary; }
    public void setHospitalStaySummary(String hospitalStaySummary) { this.hospitalStaySummary = hospitalStaySummary; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public List<Medication> getMedications() { return medications; }
    public List<FollowUp> getFollowUps() { return followUps; }
    public List<ChecklistItem> getChecklistItems() { return checklistItems; }
    public List<String> getPrecautions() { return precautions; }
    public List<String> getMissingInformation() { return missingInformation; }

    public void addMedication(Medication m) { m.setReport(this); medications.add(m); }
    public void addFollowUp(FollowUp f) { f.setReport(this); followUps.add(f); }
    public void addChecklistItem(ChecklistItem c) { c.setReport(this); checklistItems.add(c); }

    /** Removes all AI-derived data so a report can be (re)analyzed cleanly. */
    public void clearAnalysis() {
        medications.clear();
        followUps.clear();
        checklistItems.clear();
        precautions.clear();
        missingInformation.clear();
        simplifiedSummary = null;
        diagnosisAsStated = null;
        hospitalStaySummary = null;
        analyzedAt = null;
    }
}
