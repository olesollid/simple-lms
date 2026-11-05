package com._gods.canvas2.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com._gods.canvas2.service.ConflictException;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.web.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
