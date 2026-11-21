package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

/**
 * {@code maxAttempts == 0} means unlimited. {@code showHints} controls whether each question's own
 * hint is shown after an attempt got it wrong; leaving it out means on.
 */
public record QuizRequest(String title, int position, int maxAttempts, LocalDateTime deadline,
    Boolean showHints) {
}
