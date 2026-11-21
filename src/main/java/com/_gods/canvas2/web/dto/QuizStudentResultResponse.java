package com._gods.canvas2.web.dto;

import com._gods.canvas2.grading.QuizGrade;

/** One student's row in a teacher's quiz results. {@code best} is null until an attempt is graded. */
public record QuizStudentResultResponse(String email, String name, int attemptsUsed, QuizGrade best) {
}
