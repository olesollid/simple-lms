package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

import com._gods.canvas2.model.Submission;

public record SubmissionResponse(Long id, Long contentId, String submitterEmail, Long groupId,
    String lastSubmittedByEmail, String text, LocalDateTime submittedAt, Double grade,
    String feedback, LocalDateTime gradedAt) {

  public static SubmissionResponse from(Submission submission) {
    String submitterEmail = submission.getSubmitter() != null
        ? submission.getSubmitter().getEmail() : null;
    Long groupId = submission.getGroup() != null ? submission.getGroup().getId() : null;
    return new SubmissionResponse(submission.getId(), submission.getContent().getId(),
        submitterEmail, groupId, submission.getLastSubmittedBy().getEmail(), submission.getText(),
        submission.getSubmittedAt(), submission.getGrade(), submission.getFeedback(),
        submission.getGradedAt());
  }
}
