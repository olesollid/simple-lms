package com._gods.canvas2.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com._gods.canvas2.service.CourseHasDependentsException;
import com._gods.canvas2.service.CourseNotFoundException;
import com._gods.canvas2.service.ModuleHasDependentsException;
import com._gods.canvas2.service.ModuleNotFoundException;
import com._gods.canvas2.web.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CourseNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(CourseNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(CourseHasDependentsException.class)
  public ResponseEntity<ErrorResponse> handleConflict(CourseHasDependentsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(ModuleNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ModuleNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(ModuleHasDependentsException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ModuleHasDependentsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(exception.getMessage()));
  }
}
