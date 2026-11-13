package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.web.dto.EnrollmentRequest;
import com._gods.canvas2.web.dto.EnrollmentResponse;

@RestController
@RequestMapping("/api/courses/{courseId}/enrollments")
public class EnrollmentController {

  private final EnrollmentService enrollmentService;

  public EnrollmentController(EnrollmentService enrollmentService) {
    this.enrollmentService = enrollmentService;
  }

  @PostMapping
  public ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long courseId,
      @RequestBody EnrollmentRequest request, Authentication authentication) {
    Enrollment enrollment = enrollmentService.enroll(courseId, request.email(), request.role(),
        authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponse.from(enrollment));
  }

  @GetMapping
  public List<EnrollmentResponse> getEnrollments(@PathVariable Long courseId,
      @RequestParam(required = false) Role role, Authentication authentication) {
    List<Enrollment> enrollments = role == null
        ? enrollmentService.getEnrollmentsByCourse(courseId, authentication.getName())
        : enrollmentService.getEnrollmentsByCourse(courseId, role, authentication.getName());
    return enrollments.stream().map(EnrollmentResponse::from).toList();
  }

  @GetMapping("/{email}")
  public EnrollmentResponse getEnrollment(@PathVariable Long courseId,
      @PathVariable String email, Authentication authentication) {
    return EnrollmentResponse.from(
        enrollmentService.getEnrollment(courseId, email, authentication.getName()));
  }

  @PutMapping("/{email}")
  public EnrollmentResponse changeRole(@PathVariable Long courseId, @PathVariable String email,
      @RequestBody EnrollmentRequest request, Authentication authentication) {
    return EnrollmentResponse.from(enrollmentService.changeRole(courseId, email, request.role(),
        authentication.getName()));
  }

  @DeleteMapping("/{email}")
  public ResponseEntity<Void> unenroll(@PathVariable Long courseId, @PathVariable String email,
      Authentication authentication) {
    enrollmentService.unenroll(courseId, email, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
