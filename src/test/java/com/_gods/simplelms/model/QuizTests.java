package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Term;

class QuizTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction");
  }

  @Test
  void createsQuizWithValidFields() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 3);

    assertEquals(3, quiz.getMaxAttempts());
    assertNull(quiz.getDeadline());
  }

  @Test
  void createsQuizWithDeadline() {
    LocalDateTime deadline = LocalDateTime.of(2026, 1, 8, 23, 59);
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1, deadline);

    assertEquals(deadline, quiz.getDeadline());
  }

  @Test
  void rejectsNegativeMaxAttempts() {
    assertThrows(IllegalArgumentException.class, () -> new Quiz(module(), "Week 1 Quiz", 0, -1));
  }

  @Test
  void zeroMaxAttemptsMeansUnlimited() {
    assertTrue(new Quiz(module(), "Week 1 Quiz", 0, Quiz.UNLIMITED_ATTEMPTS).hasUnlimitedAttempts());
    assertFalse(new Quiz(module(), "Week 1 Quiz", 0, 3).hasUnlimitedAttempts());
  }

  @Test
  void startsAsAnInvisibleDraftAndBecomesVisibleWhenPublished() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);
    quiz.addQuestion("What is 2+2?");

    assertFalse(quiz.isPublished());
    assertFalse(quiz.isVisibleAt(LocalDateTime.now()));
    quiz.publish();
    assertTrue(quiz.isPublished());
    assertTrue(quiz.isVisibleAt(LocalDateTime.now()));
  }

  @Test
  void closesAfterItsDeadlineButNotExactlyAtIt() {
    LocalDateTime deadline = LocalDateTime.of(2026, 12, 1, 12, 0);
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1, deadline);

    assertFalse(quiz.isClosedAt(deadline));
    assertTrue(quiz.isClosedAt(deadline.plusSeconds(1)));
    quiz.updateDeadline(null);
    assertFalse(quiz.isClosedAt(deadline.plusYears(1)));
  }

  @Test
  void rejectsADeadlineThatIsAmbiguousWhenTheClocksGoBack() {
    // 25 October 2026, 02:30 happens twice in Europe/Oslo.
    LocalDateTime ambiguous = LocalDateTime.of(2026, 10, 25, 2, 30);
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);

    assertThrows(IllegalArgumentException.class, () -> quiz.updateDeadline(ambiguous));
    assertThrows(IllegalArgumentException.class,
        () -> new Quiz(module(), "Week 1 Quiz", 0, 1, ambiguous));
  }

  @Test
  void cannotPublishWithoutQuestions() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);

    assertThrows(IllegalStateException.class, quiz::publish);
  }

  @Test
  void removingAQuestionRenumbersTheRest() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);
    Question first = quiz.addQuestion("First");
    quiz.addQuestion("Second");
    Question third = quiz.addQuestion("Third", "A hint");

    quiz.removeQuestion(first);

    assertEquals(2, quiz.getQuestions().size());
    assertEquals(1, third.getPosition());
    assertEquals("A hint", third.getHint());
  }

  @Test
  void addsQuestionsInOrder() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);

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
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);
    quiz.addQuestion("What is 2+2?");

    assertThrows(UnsupportedOperationException.class,
        () -> quiz.getQuestions().add(null));
  }

  @Test
  void changesTheAttemptLimit() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);

    quiz.updateMaxAttempts(0);

    assertEquals(0, quiz.getMaxAttempts());
    assertTrue(quiz.hasUnlimitedAttempts());
  }

  @Test
  void rejectsANegativeAttemptLimitWhenChangingIt() {
    Quiz quiz = new Quiz(module(), "Week 1 Quiz", 0, 1);

    assertThrows(IllegalArgumentException.class, () -> quiz.updateMaxAttempts(-1));
    assertEquals(1, quiz.getMaxAttempts());
  }
}
