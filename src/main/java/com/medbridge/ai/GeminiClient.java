package com.medbridge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medbridge.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal client for the Gemini generateContent REST endpoint.
 * It never fabricates output: every failure becomes an {@link AiServiceException}.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int timeoutSeconds;

    public GeminiClient(ObjectMapper mapper,
                        @Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.base-url}") String baseUrl,
                        @Value("${gemini.timeout-seconds:120}") int timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** Sends the prompt and returns the raw JSON text produced by the model. */
    public String generateJson(String systemInstruction, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new AiServiceException("The Gemini API key is not configured. Set GEMINI_API_KEY "
                    + "(in the .env file or as an environment variable), restart the application, and retry the analysis.");
        }

        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", userPrompt);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");

        String bodyText;
        try {
            bodyText = mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new AiServiceException("Could not build the AI request.", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AiServiceException("The Gemini API did not respond in time. Please try again.", e);
        } catch (IOException e) {
            throw new AiServiceException("Could not reach the Gemini API. Check your internet connection and try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted. Please try again.", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new AiServiceException(describeHttpError(response.statusCode(), response.body()));
        }
        return extractText(response.body());
    }

    private String extractText(String responseBody) {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (IOException e) {
            throw new AiServiceException("The Gemini API returned an unreadable response.", e);
        }

        String blockReason = root.path("promptFeedback").path("blockReason").asText("");
        if (!blockReason.isBlank()) {
            throw new AiServiceException("The Gemini API declined to process this document (reason: " + blockReason + ").");
        }

        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
                text.append(part.get("text").asText());
            }
        }
        if (text.length() == 0) {
            log.warn("Gemini returned no text. finishReason={}", finishReason);
            throw new AiServiceException("The Gemini API returned no content"
                    + (finishReason.isBlank() ? "." : " (finish reason: " + finishReason + ")."));
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AiServiceException("The AI response was cut off because the document is too long or complex. Try a shorter document.");
        }
        return text.toString();
    }

    private String describeHttpError(int status, String body) {
        String apiMessage = "";
        try {
            apiMessage = mapper.readTree(body).path("error").path("message").asText("");
        } catch (IOException ignored) {
            // body was not JSON; fall back to the status code only
        }
        if (apiMessage.length() > 300) {
            apiMessage = apiMessage.substring(0, 300) + "...";
        }
        String hint = switch (status) {
            case 400 -> "The request was rejected (check that GEMINI_API_KEY is valid).";
            case 401, 403 -> "The API key was not accepted or does not have access.";
            case 404 -> "The model \"" + model + "\" was not found. Set GEMINI_MODEL to a currently available model.";
            case 429 -> "The Gemini API rate limit or quota was reached. Wait a moment and retry.";
            default -> status >= 500 ? "The Gemini service had a temporary problem. Please retry." : "The request failed.";
        };
        log.warn("Gemini API error {}: {}", status, apiMessage);
        return "Gemini API error (HTTP " + status + "). " + hint + (apiMessage.isBlank() ? "" : " Details: " + apiMessage);
    }
}
