package com.medbridge.service;

import com.medbridge.ai.AiAnalysisResult;
import com.medbridge.dto.ReportDetailDto;
import com.medbridge.dto.ReportSummaryDto;
import com.medbridge.entity.*;
import com.medbridge.exception.BadRequestException;
import com.medbridge.exception.ResourceNotFoundException;
import com.medbridge.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final int MAX_STORED_CHARS = 500_000;

    private final ReportRepository reportRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final ReportMapper mapper;

    public ReportService(ReportRepository reportRepository, PdfTextExtractor pdfTextExtractor, ReportMapper mapper) {
        this.reportRepository = reportRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ upload / read / delete

    @Transactional
    public ReportDetailDto upload(MultipartFile file, String patientLabel) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a PDF file to upload.");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files are supported.");
        }
        String label = patientLabel == null ? null : patientLabel.trim();
        if (label != null && label.length() > 100) {
            throw new BadRequestException("The patient label must be 100 characters or fewer.");
        }
        if (label != null && label.isEmpty()) {
            label = null;
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new BadRequestException("The file is not a valid PDF.");
        }

        String text = pdfTextExtractor.extract(bytes);
        if (text.isBlank()) {
            throw new BadRequestException("No readable text was found in this PDF. Scanned or image-only PDFs are not supported.");
        }
        if (text.length() > MAX_STORED_CHARS) {
            text = text.substring(0, MAX_STORED_CHARS);
        }

        Report report = new Report();
        report.setFileName(fileName);
        report.setPatientLabel(label);
        report.setExtractedText(text);
        report.setStatus(ReportStatus.UPLOADED);
        return mapper.toDetail(reportRepository.saveAndFlush(report));
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryDto> list() {
        return reportRepository.findAllByOrderByUploadedAtDesc().stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ReportDetailDto get(Long id) {
        return mapper.toDetail(find(id));
    }

    @Transactional
    public void delete(Long id) {
        reportRepository.delete(find(id));
    }

    // ------------------------------------------------------------ analysis support

    @Transactional(readOnly = true)
    public String getTextForAnalysis(Long id) {
        String text = find(id).getExtractedText();
        if (text == null || text.isBlank()) {
            throw new BadRequestException("This report has no extracted text to analyze.");
        }
        return text;
    }

    @Transactional
    public void markFailed(Long id, String message) {
        Report report = find(id);
        // Never overwrite a previously successful analysis because a retry failed.
        if (report.getStatus() != ReportStatus.ANALYZED) {
            report.setStatus(ReportStatus.FAILED);
            report.setErrorMessage(clip(message, 1000));
        }
    }

    @Transactional
    public ReportDetailDto saveAnalysis(Long id, AiAnalysisResult result, boolean truncated, int maxChars) {
        Report report = find(id);
        report.clearAnalysis();
        String docLower = report.getExtractedText().toLowerCase(Locale.ROOT);

        report.setSimplifiedSummary(clip(result.simplifiedSummary(), 8000));
        report.setDiagnosisAsStated(clip(blankToNull(result.diagnosisAsStated()), 4000));
        report.setHospitalStaySummary(clip(blankToNull(result.hospitalStaySummary()), 4000));

        Set<String> missing = new LinkedHashSet<>();
        for (String m : safe(result.missingInformation())) {
            if (m != null && !m.isBlank()) {
                missing.add(clip(m.trim(), 1000));
            }
        }

        // Medications: keep only those whose name can actually be found in the document text.
        for (AiAnalysisResult.MedicationItem item : safe(result.medications())) {
            if (item == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            if (!appearsInDocument(item.name(), docLower)) {
                missing.add("The AI listed a medication (\"" + clip(item.name().trim(), 80)
                        + "\") that could not be found in the document text, so it was excluded. Please check your original document.");
                continue;
            }
            Medication med = new Medication();
            med.setName(clip(item.name().trim(), 200));
            med.setDosage(clip(blankToNull(item.dosage()), 200));
            med.setFrequency(clip(blankToNull(item.frequency()), 200));
            med.setDuration(clip(blankToNull(item.duration()), 200));
            med.setInstructions(clip(blankToNull(item.instructions()), 1000));
            report.addMedication(med);
        }

        for (AiAnalysisResult.FollowUpItem item : safe(result.followUps())) {
            if (item == null) {
                continue;
            }
            FollowUp f = new FollowUp();
            f.setProvider(clip(blankToNull(item.provider()), 200));
            f.setPurpose(clip(blankToNull(item.purpose()), 500));
            f.setAppointmentDate(clip(blankToNull(item.appointmentDate()), 100));
            f.setDetails(clip(blankToNull(item.details()), 1000));
            if (f.getProvider() == null && f.getPurpose() == null && f.getAppointmentDate() == null && f.getDetails() == null) {
                continue;
            }
            report.addFollowUp(f);
        }

        for (String p : safe(result.precautions())) {
            if (p != null && !p.isBlank()) {
                report.getPrecautions().add(clip(p.trim(), 1000));
            }
        }

        if (report.getMedications().isEmpty()) {
            missing.add("No medications were found in this document.");
        }
        if (report.getFollowUps().isEmpty()) {
            missing.add("No follow-up appointments were found in this document.");
        }
        if (report.getPrecautions().isEmpty()) {
            missing.add("No precautions or warning signs were found in this document.");
        }
        if (truncated) {
            missing.add("This document is very long. Only the first " + maxChars + " characters were analyzed.");
        }
        report.getMissingInformation().addAll(missing);

        buildChecklist(report);

        report.setStatus(ReportStatus.ANALYZED);
        report.setErrorMessage(null);
        report.setAnalyzedAt(LocalDateTime.now());
        return mapper.toDetail(reportRepository.saveAndFlush(report));
    }

    // ------------------------------------------------------------ helpers

    /** Checklist is built from the validated, extracted data only (no extra AI-generated advice). */
    private void buildChecklist(Report report) {
        for (Medication m : report.getMedications()) {
            String details = joinNonNull(" | ", m.getDosage(), m.getFrequency(), m.getDuration() == null ? null : "for " + m.getDuration());
            addItem(report, ChecklistCategory.MEDICATION,
                    "Take " + m.getName() + (details.isEmpty() ? "" : " - " + details)
                            + (m.getInstructions() == null ? "" : " (" + m.getInstructions() + ")"));
        }
        for (FollowUp f : report.getFollowUps()) {
            String who = f.getProvider() == null ? "follow-up appointment" : "follow-up with " + f.getProvider();
            String extra = joinNonNull(" | ", f.getAppointmentDate(), f.getPurpose());
            addItem(report, ChecklistCategory.FOLLOW_UP, "Attend " + who + (extra.isEmpty() ? "" : " - " + extra));
        }
        for (String p : report.getPrecautions()) {
            addItem(report, ChecklistCategory.PRECAUTION, p);
        }
    }

    private void addItem(Report report, ChecklistCategory category, String description) {
        ChecklistItem item = new ChecklistItem();
        item.setCategory(category);
        item.setDescription(clip(description, 1000));
        report.addChecklistItem(item);
    }

    private Report find(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report " + id + " was not found."));
    }

    private static boolean appearsInDocument(String name, String docLower) {
        String longest = Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 3 && !t.chars().allMatch(Character::isDigit))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
        return longest == null || docLower.contains(longest);
    }

    private static String sanitizeFileName(String original) {
        String name = original == null ? "report.pdf" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "report.pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String joinNonNull(String sep, String... parts) {
        return Arrays.stream(parts).filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(sep));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim())) ? null : s.trim();
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
