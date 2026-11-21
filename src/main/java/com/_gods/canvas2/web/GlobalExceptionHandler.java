package com._gods.canvas2.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com._gods.canvas2.aitutor.RateLimitExceededException;
import com._gods.canvas2.aitutor.TutorChatService;
import com._gods.canvas2.service.ConflictException;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.web.dto.ErrorResponse;
import com._gods.canvas2.web.dto.RateLimitErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadTooLarge(Exception exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(new ErrorResponse("File exceeds the 10 MB upload limit"));
  }

  @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingUpload(Exception exception) {
    return ResponseEntity.badRequest().body(new ErrorResponse("Select a file to upload"));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<RateLimitErrorResponse> handleRateLimited(RateLimitExceededException exception) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
        .body(new RateLimitErrorResponse(exception.getMessage(), exception.getRetryAfterSeconds()));
  }

  @ExceptionHandler(TutorChatService.Unavailable.class)
  public ResponseEntity<ErrorResponse> handleTutorUnavailable(TutorChatService.Unavailable exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(exception.getMessage()));
  }

  /** Covers unknown enum values such as an invalid role in a body or query parameter. */
  @ExceptionHandler({HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ErrorResponse> handleUnreadableRequest(Exception exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Malformed request"));
  }

  /**
   * Fallback for anything not covered above, so an unexpected bug still returns this API's own
   * {@link ErrorResponse} shape instead of Spring Boot's default error body.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("An unexpected error occurred"));
  }
}
