package com._gods.canvas2.service;

public class CourseNotFoundException extends NotFoundException {
  public CourseNotFoundException(Long id) {
    super("Course not found: " + id);
  }
}
