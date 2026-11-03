package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Term;

public record CourseResponse(Long id, String title, String description, Term term, int year) {
  public static CourseResponse from(Course course) {
    return new CourseResponse(course.getId(), course.getTitle(), course.getDescription(),
        course.getTerm(), course.getYear());
  }
}
