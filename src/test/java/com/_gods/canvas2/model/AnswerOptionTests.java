package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnswerOptionTests {

  private Question question() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1: Introduction");
    Quiz quiz = new Quiz(module, "Week 1 Quiz", 0, 1);
    return new Question(quiz, "What is 2+2?", 0);
  }

  @Test
  void createsOptionWithValidFields() {
    AnswerOption option = new AnswerOption(question(), "4", true);

    assertEquals("4", option.getText());
    assertTrue(option.isCorrect());
  }

  @Test
  void createsIncorrectOption() {
    AnswerOption option = new AnswerOption(question(), "5", false);

    assertFalse(option.isCorrect());
  }

  @Test
  void rejectsNullQuestion() {
    assertThrows(IllegalArgumentException.class, () -> new AnswerOption(null, "4", true));
  }

  @Test
  void rejectsBlankText() {
    assertThrows(IllegalArgumentException.class, () -> new AnswerOption(question(), "  ", true));
  }
}
