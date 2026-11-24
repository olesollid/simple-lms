package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.CodingExercise;
import com._gods.simplelms.model.Content;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.StaticPage;

public record ContentResponse(Long id, Long moduleId, String type, String title, int position,
    LocalDateTime visibleFrom, LocalDateTime visibleUntil, String body, String instructions,
    String repositoryUrl, LocalDateTime deadline, Boolean lateSubmissionsAccepted,
    Boolean groupSubmission, Integer maxAttempts, Boolean published, Boolean closed,
    boolean visibleToStudents, Boolean showResultsImmediately, Boolean resultsPublished) {

  public static ContentResponse from(Content content) {
    String body = content instanceof StaticPage staticPage ? staticPage.getBody() : null;
    String instructions = content instanceof Assignment assignment
        ? assignment.getInstructions() : null;
    String repositoryUrl = content instanceof CodingExercise codingExercise
        ? codingExercise.getRepositoryUrl() : null;
    LocalDateTime deadline = switch (content) {
      case Assignment assignment -> assignment.getDeadline();
      case CodingExercise codingExercise -> codingExercise.getDeadline();
      case Quiz quiz -> quiz.getDeadline();
      default -> null;
    };
    Boolean lateSubmissionsAccepted = switch (content) {
      case Assignment assignment -> assignment.isLateSubmissionsAccepted();
      case CodingExercise codingExercise -> codingExercise.isLateSubmissionsAccepted();
      default -> null;
    };
    Boolean groupSubmission = switch (content) {
      case Assignment assignment -> assignment.isGroupSubmission();
      case CodingExercise codingExercise -> codingExercise.isGroupSubmission();
      default -> null;
    };
    return new ContentResponse(content.getId(), content.getModule().getId(),
        content.getClass().getSimpleName(), content.getTitle(), content.getPosition(),
        content.getVisibleFrom(), content.getVisibleUntil(), body, instructions, repositoryUrl,
        deadline, lateSubmissionsAccepted, groupSubmission,
        content instanceof Quiz quiz ? quiz.getMaxAttempts() : null,
        content instanceof Quiz quiz ? quiz.isPublished() : null,
        content instanceof Quiz quiz ? quiz.isClosedAt(LocalDateTime.now(HandInPolicy.ZONE)) : null,
        content.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE)),
        content instanceof Quiz quiz ? quiz.isShowResultsImmediately() : null,
        content instanceof Quiz quiz ? quiz.isGradesPublished() : null);
  }
}
