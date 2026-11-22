package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import com._gods.canvas2.model.Role;

/** Self-only summary. Hidden coursework and unpublished grades/feedback are excluded. */
public record MyDeliveriesResponse(Long courseId, Role role, List<Activity> activities) {
  public enum Status { NOT_SUBMITTED, AWAITING_GRADING, AWAITING_PUBLICATION, GRADED }

  public record Activity(Long contentId, Long moduleId, String title, String type,
      LocalDateTime deadline, Status status, Double grade, String feedback,
      Boolean lateSubmission, boolean groupSubmission, boolean needsGroup,
      boolean gradingPending, boolean overdue) {}
}
