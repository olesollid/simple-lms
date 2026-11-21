package com._gods.canvas2.web.dto;

import java.util.List;

import com._gods.canvas2.model.Question;

/** Teacher-facing only: options carry {@code correct}, so never serve this to students. */
public record QuestionWithAnswersResponse(Long id, String text, int position, String hint,
    List<AnswerOptionWithCorrectnessResponse> options) {
  public static QuestionWithAnswersResponse from(Question question) {
    List<AnswerOptionWithCorrectnessResponse> options = question.getOptions().stream()
        .map(AnswerOptionWithCorrectnessResponse::from)
        .toList();
    return new QuestionWithAnswersResponse(question.getId(), question.getText(), question.getPosition(),
        question.getHint(), options);
  }
}
