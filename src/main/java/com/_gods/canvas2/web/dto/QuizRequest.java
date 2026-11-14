package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

public record QuizRequest(String title, int position, int maxAttempts, boolean showHints,
    LocalDateTime deadline) {
}
