package com._gods.canvas2.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.service.StudentDeliveriesService;
import com._gods.canvas2.web.dto.StudentDeliveriesResponse;

@RestController
public class StudentDeliveriesController {
  private final StudentDeliveriesService deliveries;

  public StudentDeliveriesController(StudentDeliveriesService deliveries) {
    this.deliveries = deliveries;
  }

  @GetMapping("/api/courses/{courseId}/students/{email}/deliveries")
  public StudentDeliveriesResponse get(@PathVariable Long courseId, @PathVariable String email,
      Authentication authentication) {
    return deliveries.getDeliveries(courseId, email, authentication.getName());
  }
}
