package com.llmcr.api;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class APIExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(APIExceptionHandler.class);

    public record ErrorResponse(String code, String error, String message, int status, String path, Instant timestamp) {
    }

    @ExceptionHandler(APIServiceException.class)
    public ResponseEntity<ErrorResponse> handleApiServiceException(APIServiceException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        logger.error("[APIService] {} on {}: {}", ex.getErrorCode().code(), request.getRequestURI(), ex.getMessage(),
                ex);

        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode().code(),
                status.getReasonPhrase(),
                messageOrDefault(ex, ex.getErrorCode().defaultMessage()),
                status.value(),
                request.getRequestURI(),
                Instant.now());

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception ex, HttpServletRequest request) {
        logger.warn("[APIService] Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        return new ErrorResponse(
                "illegalargument",
                "Bad Request",
                messageOrDefault(ex, "Invalid request payload or parameters"),
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex, HttpServletRequest request) {
        logger.error("[APIService] Unexpected error on {}", request.getRequestURI(), ex);
        return new ErrorResponse(
                "internalerror",
                "Internal Server Error",
                "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI(),
                Instant.now());
    }

    private static String messageOrDefault(Exception ex, String fallback) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }

}
