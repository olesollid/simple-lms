package com._gods.canvas2.web.dto;

import java.util.List;

import com._gods.canvas2.model.Question;

/** Student-facing: options never carry the {@code correct} flag. */
/**
 * A question as students see it: no correct flags and no hint. Hints are only revealed for the
 * questions a student got wrong, through {@link QuizAttemptsResponse#hints()}.
 */
public record QuestionResponse(Long id, String text, int position,
    List<AnswerOptionResponse> options) {
  public static QuestionResponse from(Question question) {
    List<AnswerOptionResponse> options = question.getOptions().stream()
        .map(AnswerOptionResponse::from)
        .toList();
    return new QuestionResponse(question.getId(), question.getText(), question.getPosition(), options);
  }
}
