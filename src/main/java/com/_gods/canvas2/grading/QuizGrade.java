package com._gods.canvas2.grading;

import java.time.LocalDateTime;

/** Stored in Redis, keyed by quiz and student. */
public record QuizGrade(Long quizId, String studentEmail, int correctQuestions, int totalQuestions,
    double score, LocalDateTime gradedAt) {
}
