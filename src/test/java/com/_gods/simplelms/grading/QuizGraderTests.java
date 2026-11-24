package com._gods.simplelms.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.grading.QuizGrader;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Term;

/**
 * Pure-logic edge cases that don't need persisted option ids. Id-based correctness (matching
 * selected option ids against the real correct set, and the "unanswered question" case, which
 * needs a real question id to build the answers map against) is covered in
 * {@link QuizGraderDataTests}, which persists real entities so ids are assigned.
 */
class QuizGraderTests {

  private final QuizGrader grader = new QuizGrader();

  @Test
  void returnsZeroScoreForQuizWithNoQuestions() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1");
    Quiz quiz = new Quiz(module, "Week 1 Quiz", 0, 1);

    QuizGrade grade = grader.grade(quiz, "student@example.com", Map.of());

    assertEquals(0, grade.totalQuestions());
    assertEquals(0, grade.correctQuestions());
    assertEquals(0.0, grade.score());
  }
}
