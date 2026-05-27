package com.llmcr.domain.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class APIExceptionHandler {

  public record ErrorResponse(
      String code, String error, String message, int status, String path, Instant timestamp) {}

  @ExceptionHandler(APIServiceException.class)
  public ResponseEntity<ErrorResponse> handleApiServiceException(
      APIServiceException ex, HttpServletRequest request) {
    return buildResponse(ex.getErrorCode(), resolveMessage(ex), request.getRequestURI());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return buildResponse(
        APIServiceException.ErrorCode.INVALID_REQUEST,
        resolveMessage(ex, APIServiceException.ErrorCode.INVALID_REQUEST),
        request.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    return buildResponse(
        APIServiceException.ErrorCode.INTERNAL_ERROR,
        APIServiceException.ErrorCode.INTERNAL_ERROR.message(),
        request.getRequestURI());
  }

  private ResponseEntity<ErrorResponse> buildResponse(
      APIServiceException.ErrorCode errorCode, String message, String path) {
    HttpStatus status = errorCode.status();
    ErrorResponse response =
        new ErrorResponse(
            errorCode.code(),
            status.getReasonPhrase(),
            message,
            status.value(),
            path,
            Instant.now());
    return ResponseEntity.status(status).body(response);
  }

  private String resolveMessage(APIServiceException ex) {
    return resolveMessage(ex, ex.getErrorCode());
  }

  private String resolveMessage(Exception ex, APIServiceException.ErrorCode fallback) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return fallback.message();
    }
    return message;
  }
}
