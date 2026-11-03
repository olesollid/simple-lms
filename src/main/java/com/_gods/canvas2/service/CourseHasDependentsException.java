package com._gods.canvas2.service;

public class CourseHasDependentsException extends RuntimeException {
  public CourseHasDependentsException(Long id) {
    super("Course " + id + " has modules or enrollments and cannot be deleted until they are removed");
  }
}
