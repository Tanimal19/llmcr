package com.llmcr.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.llmcr.domain.exception.APIExceptionHandler.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class APIExceptionHandlerTest {

  private static final String REQUEST_PATH = "/api/test";

  private APIExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new APIExceptionHandler();
    request = new MockHttpServletRequest();
    request.setRequestURI(REQUEST_PATH);
  }

  @Test
  void handleApiServiceExceptionReturnsErrorCodeSpecificResponse() {
    APIServiceException exception =
        new APIServiceException(
            APIServiceException.ErrorCode.RAG_RETRIEVAL_FAILED, "custom retrieval failure message");

    var response = handler.handleApiServiceException(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    ErrorResponse body = response.getBody();
    assertThat(body.code()).isEqualTo("ragretrievalfailed");
    assertThat(body.error()).isEqualTo("Internal Server Error");
    assertThat(body.message()).isEqualTo("custom retrieval failure message");
    assertThat(body.status()).isEqualTo(500);
    assertThat(body.path()).isEqualTo(REQUEST_PATH);
    assertThat(body.timestamp()).isNotNull();
  }

  @Test
  void handleIllegalArgumentReturnsInvalidRequestResponse() {
    IllegalArgumentException exception = new IllegalArgumentException("bad parameter");

    var response = handler.handleIllegalArgument(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    ErrorResponse body = response.getBody();
    assertThat(body.code()).isEqualTo("invalidrequest");
    assertThat(body.error()).isEqualTo("Bad Request");
    assertThat(body.message()).isEqualTo("bad parameter");
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.path()).isEqualTo(REQUEST_PATH);
    assertThat(body.timestamp()).isNotNull();
  }

  @Test
  void handleIllegalArgumentFallsBackToDefaultMessageWhenBlank() {
    IllegalArgumentException exception = new IllegalArgumentException(" ");

    var response = handler.handleIllegalArgument(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    ErrorResponse body = response.getBody();
    assertThat(body.code()).isEqualTo("invalidrequest");
    assertThat(body.message()).isEqualTo("Invalid request payload or parameters");
  }

  @Test
  void handleUnexpectedReturnsInternalErrorResponse() {
    RuntimeException exception = new RuntimeException("boom");

    var response = handler.handleUnexpected(exception, request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    ErrorResponse body = response.getBody();
    assertThat(body.code()).isEqualTo("internalerror");
    assertThat(body.error()).isEqualTo("Internal Server Error");
    assertThat(body.message()).isEqualTo("An unexpected error occurred");
    assertThat(body.status()).isEqualTo(500);
    assertThat(body.path()).isEqualTo(REQUEST_PATH);
    assertThat(body.timestamp()).isNotNull();
  }
}
