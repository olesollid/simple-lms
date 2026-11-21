package com._gods.canvas2.web.dto;

import java.util.List;

import com._gods.canvas2.model.Question;

/**
 * {@code type} is {@code CHOICE} or {@code ORDERING}; leaving it out means {@code CHOICE}. For an
 * ordering question the options are listed in their correct order and {@code correct} is ignored.
 */
public record QuestionRequest(String text, String hint, List<AnswerOptionRequest> options,
    Question.Type type) {

  public QuestionRequest(String text, String hint, List<AnswerOptionRequest> options) {
    this(text, hint, options, null);
  }
}
