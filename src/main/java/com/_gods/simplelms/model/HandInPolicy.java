package com._gods.simplelms.model;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * All course times -- deadlines and content visibility windows -- are wall-clock times in
 * Europe/Oslo, whatever time zone the server or the browser runs in.
 */
public final class HandInPolicy {
  public static final ZoneId ZONE = ZoneId.of("Europe/Oslo");

  private HandInPolicy() {}

  public static LocalDateTime deadline(Content content) {
    return switch (content) {
      case Assignment assignment -> assignment.getDeadline();
      case CodingExercise exercise -> exercise.getDeadline();
      case Quiz quiz -> quiz.getDeadline();
      default -> null;
    };
  }

  public static boolean acceptsLate(Content content) {
    return switch (content) {
      case Assignment assignment -> assignment.isLateSubmissionsAccepted();
      case CodingExercise exercise -> exercise.isLateSubmissionsAccepted();
      default -> false;
    };
  }

  public static boolean isLate(Content content, LocalDateTime submittedAt) {
    LocalDateTime deadline = deadline(content);
    return deadline != null && submittedAt.isAfter(deadline);
  }

  public static void validateDeadline(LocalDateTime deadline) {
    if (deadline != null && ZONE.getRules().getValidOffsets(deadline).size() != 1) {
      throw new IllegalArgumentException("Choose an unambiguous Europe/Oslo deadline outside the daylight-saving clock change");
    }
  }
}
