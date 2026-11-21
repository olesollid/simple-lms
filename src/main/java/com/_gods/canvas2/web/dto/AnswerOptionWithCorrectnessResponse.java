package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.AnswerOption;

/** Teacher-facing only: includes {@code correct} and the answers, so never serve this to students. */
public record AnswerOptionWithCorrectnessResponse(Long id, String text, boolean correct,
    String matchText) {
  public static AnswerOptionWithCorrectnessResponse from(AnswerOption option) {
    return new AnswerOptionWithCorrectnessResponse(option.getId(), option.getText(),
        option.isCorrect(), option.getMatchText());
  }
}
