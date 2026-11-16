package com.medbridge.controller;

import com.medbridge.dto.ReportDetailDto;
import com.medbridge.dto.ReportSummaryDto;
import com.medbridge.service.AnalysisService;
import com.medbridge.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final AnalysisService analysisService;

    public ReportController(ReportService reportService, AnalysisService analysisService) {
        this.reportService = reportService;
        this.analysisService = analysisService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportDetailDto> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "patientLabel", required = false) String patientLabel) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.upload(file, patientLabel));
    }

    @PostMapping("/{id}/analyze")
    public ReportDetailDto analyze(@PathVariable Long id) {
        return analysisService.analyze(id);
    }

    @GetMapping
    public List<ReportSummaryDto> list() {
        return reportService.list();
    }

    @GetMapping("/{id}")
    public ReportDetailDto get(@PathVariable Long id) {
        return reportService.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
