package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

import com._gods.canvas2.model.Submission;

public record SubmissionResponse(Long id, Long contentId, String submitterEmail, Long groupId,
    String lastSubmittedByEmail, String text, LocalDateTime submittedAt, Double grade,
    String feedback, LocalDateTime gradedAt, boolean gradingComplete,
    String fileName, String fileContentType, Long fileSize, Boolean lateSubmission) {

  public static SubmissionResponse from(Submission submission) {
    return from(submission, true);
  }

  public static SubmissionResponse forStudent(Submission submission) {
    return from(submission, submission.getContent().isGradesPublished());
  }

  private static SubmissionResponse from(Submission submission, boolean revealGrade) {
    String submitterEmail = submission.getSubmitter() != null
        ? submission.getSubmitter().getEmail() : null;
    Long groupId = submission.getGroup() != null ? submission.getGroup().getId() : null;
    return new SubmissionResponse(submission.getId(), submission.getContent().getId(),
        submitterEmail, groupId, submission.getLastSubmittedBy().getEmail(), submission.getText(),
        submission.getSubmittedAt(), revealGrade ? submission.getGrade() : null,
        revealGrade ? submission.getFeedback() : null,
        revealGrade ? submission.getGradedAt() : null, submission.isGraded(),
        submission.getFile() == null ? null : submission.getFile().getFilename(),
        submission.getFile() == null ? null : submission.getFile().getContentType(),
        submission.getFile() == null ? null : submission.getFile().getSize(), submission.getLateSubmission());
  }
}
