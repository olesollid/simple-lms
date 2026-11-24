package com._gods.simplelms.web.dto;

import com._gods.simplelms.grading.QuizGrade;

/** One student's row in a teacher's quiz results. {@code best} is null until an attempt is graded. */
public record QuizStudentResultResponse(String email, String name, int attemptsUsed, QuizGrade best) {
}
