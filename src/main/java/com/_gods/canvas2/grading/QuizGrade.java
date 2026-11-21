package com._gods.canvas2.grading;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The result of one graded quiz attempt, stored in Redis. {@code score} is a fraction between 0
 * and 1. {@code incorrectQuestionIds} lets the UI show hints for exactly the questions the student
 * got wrong, without revealing the correct answers.
 */
public record QuizGrade(Long quizId, String studentEmail, int attemptNumber, int correctQuestions,
    int totalQuestions, double score, List<Long> incorrectQuestionIds, LocalDateTime gradedAt) {
}
