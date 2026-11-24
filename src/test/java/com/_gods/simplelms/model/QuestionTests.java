package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.AnswerOption;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Term;

class QuestionTests {

  private Quiz quiz() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1: Introduction");
    return new Quiz(module, "Week 1 Quiz", 0, 1);
  }

  @Test
  void createsQuestionWithValidFields() {
    Question question = new Question(quiz(), "What is 2+2?", 0);

    assertEquals("What is 2+2?", question.getText());
    assertEquals(0, question.getPosition());
    assertTrue(question.getOptions().isEmpty());
  }

  @Test
  void rejectsNullQuiz() {
    assertThrows(IllegalArgumentException.class, () -> new Question(null, "What is 2+2?", 0));
  }

  @Test
  void rejectsBlankText() {
    assertThrows(IllegalArgumentException.class, () -> new Question(quiz(), "  ", 0));
  }

  @Test
  void rejectsNegativePosition() {
    assertThrows(IllegalArgumentException.class, () -> new Question(quiz(), "What is 2+2?", -1));
  }

  @Test
  void addsOptionsAndTracksCorrectness() {
    Question question = new Question(quiz(), "Which are prime?", 0);

    AnswerOption two = question.addOption("2", true);
    AnswerOption three = question.addOption("3", true);
    AnswerOption four = question.addOption("4", false);

    assertEquals(3, question.getOptions().size());
    assertTrue(two.isCorrect());
    assertTrue(three.isCorrect());
    assertEquals(false, four.isCorrect());
  }

  @Test
  void optionsListIsUnmodifiable() {
    Question question = new Question(quiz(), "Which are prime?", 0);
    question.addOption("2", true);

    assertThrows(UnsupportedOperationException.class,
        () -> question.getOptions().add(null));
  }
}
