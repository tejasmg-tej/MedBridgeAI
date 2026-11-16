package com.medbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbridge.ai.AiAnalysisResult;
import com.medbridge.ai.GeminiClient;
import com.medbridge.ai.PromptTemplates;
import com.medbridge.dto.ReportDetailDto;
import com.medbridge.exception.AiServiceException;
import com.medbridge.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the AI analysis. Intentionally NOT transactional so no database
 * connection is held open while waiting for the Gemini API.
 */
@Service
public class AnalysisService {

    private final ReportService reportService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final int maxChars;

    public AnalysisService(ReportService reportService, GeminiClient geminiClient, ObjectMapper objectMapper,
                           @Value("${medbridge.max-text-chars:60000}") int maxChars) {
        this.reportService = reportService;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.maxChars = maxChars;
    }

    public ReportDetailDto analyze(Long reportId) {
        String fullText = reportService.getTextForAnalysis(reportId);
        boolean truncated = fullText.length() > maxChars;
        String text = truncated ? fullText.substring(0, maxChars) : fullText;

        AiAnalysisResult result;
        try {
            String json = geminiClient.generateJson(PromptTemplates.SYSTEM_INSTRUCTION, PromptTemplates.userPrompt(text));
            result = parse(json);
        } catch (AiServiceException ex) {
            reportService.markFailed(reportId, ex.getMessage());
            throw ex;
        }

        if (Boolean.FALSE.equals(result.looksLikeDischargeSummary())) {
            String msg = "This document does not appear to be a hospital discharge summary, so no instructions were generated.";
            reportService.markFailed(reportId, msg);
            throw new BadRequestException(msg);
        }
        if (result.simplifiedSummary() == null || result.simplifiedSummary().isBlank()) {
            String msg = "The AI response was incomplete (no summary was produced). Please try again.";
            reportService.markFailed(reportId, msg);
            throw new AiServiceException(msg);
        }
        return reportService.saveAnalysis(reportId, result, truncated, maxChars);
    }

    private AiAnalysisResult parse(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            int newline = json.indexOf('\n');
            json = newline > 0 ? json.substring(newline + 1) : json.substring(3);
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();
        }
        try {
            return objectMapper.readValue(json, AiAnalysisResult.class);
        } catch (JsonProcessingException e) {
            throw new AiServiceException("The AI returned a response that could not be read. Please try analyzing again.", e);
        }
    }
}
