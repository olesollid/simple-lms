package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.AnswerOption;

/** Deliberately omits {@code correct}: this is the shape returned to students. */
public record AnswerOptionResponse(Long id, String text) {
  public static AnswerOptionResponse from(AnswerOption option) {
    return new AnswerOptionResponse(option.getId(), option.getText());
  }
}
