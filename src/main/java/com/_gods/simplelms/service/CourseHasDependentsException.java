package com._gods.simplelms.service;

public class CourseHasDependentsException extends ConflictException {
  public CourseHasDependentsException(Long id) {
    super("Course " + id + " has modules or enrollments and cannot be deleted until they are removed");
  }
}
