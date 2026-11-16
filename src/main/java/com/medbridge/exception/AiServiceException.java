package com.medbridge.exception;

/** Thrown when the Gemini API is unreachable, misconfigured, or returns unusable output. */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) { super(message); }
    public AiServiceException(String message, Throwable cause) { super(message, cause); }
}
