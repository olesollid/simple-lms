package com._gods.canvas2.web.dto;

import java.util.List;
import java.util.Map;

/**
 * A student's answers, keyed by question id. {@code answers} holds option ids (CHOICE: the selected
 * ones; ORDERING: all of them in the chosen order; MATCHING: one right-hand id per left-hand item,
 * in order). {@code textAnswers} holds the typed answer for each blank of a FILL_IN question.
 * Either map may be left out.
 */
public record QuizSubmissionRequest(Map<Long, List<Long>> answers,
    Map<Long, List<String>> textAnswers) {

  public QuizSubmissionRequest(Map<Long, List<Long>> answers) {
    this(answers, null);
  }
}
