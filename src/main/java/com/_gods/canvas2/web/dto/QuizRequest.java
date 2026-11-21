package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

/**
 * {@code maxAttempts == 0} means unlimited. Hints are set per question, not per quiz.
 * {@code showResultsImmediately} defaults to true when left out.
 */
public record QuizRequest(String title, int position, int maxAttempts, LocalDateTime deadline,
    Boolean showResultsImmediately) {
}
