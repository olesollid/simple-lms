package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class CodingExerciseTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction", 0);
  }

  @Test
  void createsCodingExerciseWithDefaults() {
    CodingExercise exercise = new CodingExercise(module(), "Exercise 1", 0, "git@example.com:repo.git");

    assertEquals("git@example.com:repo.git", exercise.getRepositoryUrl());
    assertFalse(exercise.isLateSubmissionsAccepted());
    assertFalse(exercise.isGroupSubmission());
  }

  @Test
  void createsCodingExerciseWithAllFields() {
    LocalDateTime deadline = LocalDateTime.of(2026, 1, 8, 23, 59);
    CodingExercise exercise = new CodingExercise(module(), "Exercise 1", 0,
        "git@example.com:repo.git", deadline, true, true);

    assertEquals(deadline, exercise.getDeadline());
    assertTrue(exercise.isLateSubmissionsAccepted());
    assertTrue(exercise.isGroupSubmission());
  }

  @Test
  void rejectsBlankRepositoryUrl() {
    assertThrows(IllegalArgumentException.class, () -> new CodingExercise(module(), "Exercise 1", 0, "  "));
  }
}
