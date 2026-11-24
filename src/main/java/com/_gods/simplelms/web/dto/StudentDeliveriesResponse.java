package com._gods.simplelms.web.dto;

import java.util.List;

/** Teacher-only snapshot; includes unpublished grades and activities with no submission. */
public record StudentDeliveriesResponse(Long courseId, String courseTitle, String studentEmail,
    String studentName, List<Delivery> deliveries) {
  public record QuizSummary(int attemptsUsed, int latestGradedAttempt, Double bestScore) {}

  public record Delivery(ContentResponse content, String moduleTitle, boolean availableToStudent,
      boolean gradesVisibleToStudent, SubmissionResponse submission, QuizSummary quiz) {}
}
