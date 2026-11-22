package com._gods.canvas2.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com._gods.canvas2.service.MyDeliveriesService;
import com._gods.canvas2.web.dto.MyDeliveriesResponse;

@RestController
public class MyDeliveriesController {
  private final MyDeliveriesService deliveries;

  public MyDeliveriesController(MyDeliveriesService deliveries) { this.deliveries = deliveries; }

  @GetMapping("/api/courses/{courseId}/my-deliveries")
  public MyDeliveriesResponse get(@PathVariable Long courseId, Authentication authentication) {
    return deliveries.getOwnDeliveries(courseId, authentication.getName());
  }
}
