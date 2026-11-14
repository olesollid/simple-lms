package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class QuizTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction");
  }

  @Test
  void createsQuizWithValidFields() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 3, true);

    assertEquals(3, quiz.getMaxAttempts());
    assertTrue(quiz.isShowHints());
    assertNull(quiz.getDeadline());
  }

  @Test
  void createsQuizWithDeadline() {
    LocalDateTime deadline = LocalDateTime.of(2026, 1, 8, 23, 59);
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1, false, deadline);

    assertEquals(deadline, quiz.getDeadline());
    assertFalse(quiz.isShowHints());
  }

  @Test
  void rejectsNonPositiveMaxAttempts() {
    assertThrows(IllegalArgumentException.class, () -> new Quiz(module(), "Week 1 Quiz", 0, 0, true));
  }

  @Test
  void addsQuestionsInOrder() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1, false);

    Question first = quiz.addQuestion("What is 2+2?");
    Question second = quiz.addQuestion("What is 3+3?");

    assertEquals(2, quiz.getQuestions().size());
    assertEquals(first, quiz.getQuestions().get(0));
    assertEquals(second, quiz.getQuestions().get(1));
    assertEquals(0, first.getPosition());
    assertEquals(1, second.getPosition());
  }

  @Test
  void questionsListIsUnmodifiable() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1, false);
    quiz.addQuestion("What is 2+2?");

    assertThrows(UnsupportedOperationException.class,
        () -> quiz.getQuestions().add(null));
  }
}
