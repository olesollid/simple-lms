package com._gods.simplelms.service;

public class CourseNotFoundException extends NotFoundException {
  public CourseNotFoundException(Long id) {
    super("Course not found: " + id);
  }
}
