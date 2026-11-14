package com._gods.canvas2.service;

/** No grade exists yet for this quiz/student pair -- likely still being graded asynchronously. */
public class QuizGradeNotFoundException extends NotFoundException {
  public QuizGradeNotFoundException(Long quizId, String studentEmail) {
    super("No grade found for quiz " + quizId + " and student " + studentEmail);
  }
}
