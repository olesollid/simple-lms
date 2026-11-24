package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Term;

class AssignmentTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction");
  }

  @Test
  void createsAssignmentWithDefaults() {
    Assignment assignment = new Assignment(module(), "Essay 1", 0, "Write 500 words on X.");

    assertEquals("Write 500 words on X.", assignment.getInstructions());
    assertFalse(assignment.isLateSubmissionsAccepted());
    assertFalse(assignment.isGroupSubmission());
  }

  @Test
  void createsAssignmentWithAllFields() {
    LocalDateTime deadline = LocalDateTime.of(2026, 1, 8, 23, 59);
    Assignment assignment = new Assignment(module(), "Essay 1", 0, "Write 500 words on X.",
        deadline, true, true);

    assertEquals(deadline, assignment.getDeadline());
    assertTrue(assignment.isLateSubmissionsAccepted());
    assertTrue(assignment.isGroupSubmission());
  }

  @Test
  void rejectsBlankInstructions() {
    assertThrows(IllegalArgumentException.class, () -> new Assignment(module(), "Essay 1", 0, "  "));
  }
}
