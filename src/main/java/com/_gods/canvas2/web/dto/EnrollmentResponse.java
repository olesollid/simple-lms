package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;

public record EnrollmentResponse(Long id, Long courseId, String courseTitle, String email,
    String name, Role role, LocalDateTime enrolledAt) {
  public static EnrollmentResponse from(Enrollment enrollment) {
    return new EnrollmentResponse(enrollment.getId(), enrollment.getCourse().getId(),
        enrollment.getCourse().getTitle(), enrollment.getUser().getEmail(),
        enrollment.getUser().getName(), enrollment.getRole(), enrollment.getEnrolledAt());
  }
}
