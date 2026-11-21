package com._gods.canvas2.web.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com._gods.canvas2.model.Question;

/** Student-facing: options never carry the {@code correct} flag. */
/**
 * A question as students see it: no correct flags and no hint. Hints are only revealed for the
 * questions a student got wrong, through {@link QuizAttemptsResponse#hints()}.
 */
public record QuestionResponse(Long id, String text, int position, Question.Type type,
    List<AnswerOptionResponse> options) {
  public static QuestionResponse from(Question question) {
    List<AnswerOptionResponse> options = question.getOptions().stream()
        .map(AnswerOptionResponse::from)
        .collect(Collectors.toCollection(ArrayList::new));
    if (question.getType() == Question.Type.ORDERING) {
      shuffleDifferently(options);
    }
    return new QuestionResponse(question.getId(), question.getText(), question.getPosition(),
        question.getType(), options);
  }

  /** The stored order of an ordering question is its answer, so students must never get it as is. */
  private static void shuffleDifferently(List<AnswerOptionResponse> options) {
    List<AnswerOptionResponse> original = List.copyOf(options);
    Collections.shuffle(options);
    if (options.size() > 1 && options.equals(original)) {
      Collections.rotate(options, 1);
    }
  }
}
