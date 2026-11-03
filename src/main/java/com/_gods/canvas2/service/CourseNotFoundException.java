package com._gods.canvas2.service;

public class CourseNotFoundException extends RuntimeException {
  public CourseNotFoundException(Long id) {
    super("Course not found: " + id);
  }
}
