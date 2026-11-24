package com._gods.simplelms.web.dto;

/**
 * One option as the teacher writes it. {@code correct} matters for CHOICE only. {@code matchText}
 * is the right-hand item of a MATCHING pair. For FILL_IN, {@code text} holds the accepted answers of
 * one blank separated by {@code |}.
 */
public record AnswerOptionRequest(String text, boolean correct, String matchText) {

  public AnswerOptionRequest(String text, boolean correct) {
    this(text, correct, null);
  }
}
