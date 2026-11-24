package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Term;

public record CourseResponse(Long id, String title, String description, Term term, int year) {
  public static CourseResponse from(Course course) {
    return new CourseResponse(course.getId(), course.getTitle(), course.getDescription(),
        course.getTerm(), course.getYear());
  }
}
