package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.AnswerOption;

/** Teacher-facing only: includes {@code correct} and the answers, so never serve this to students. */
public record AnswerOptionWithCorrectnessResponse(Long id, String text, boolean correct,
    String matchText) {
  public static AnswerOptionWithCorrectnessResponse from(AnswerOption option) {
    return new AnswerOptionWithCorrectnessResponse(option.getId(), option.getText(),
        option.isCorrect(), option.getMatchText());
  }
}
