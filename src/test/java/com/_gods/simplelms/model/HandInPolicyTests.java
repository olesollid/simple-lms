package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Term;

class HandInPolicyTests {
  @Test
  void deadlineBoundaryIsInclusiveAndNoDeadlineNeverCountsAsLate() {
    Module module = new Module(new Course("Test", "desc", Term.AUTUMN, 2026), "Week 1");
    LocalDateTime deadline = LocalDateTime.of(2026, 11, 30, 12, 0);
    Assignment assignment = new Assignment(module, "Essay", 0, "Write", deadline, false, false);
    assertFalse(HandInPolicy.isLate(assignment, deadline.minusNanos(1)));
    assertFalse(HandInPolicy.isLate(assignment, deadline));
    assertTrue(HandInPolicy.isLate(assignment, deadline.plusNanos(1)));
    assignment.updateSubmissionPolicy(null, false);
    assertFalse(HandInPolicy.isLate(assignment, deadline.plusYears(10)));
  }

  @Test
  void rejectsNonexistentAndAmbiguousOsloWallTimes() {
    assertThrows(IllegalArgumentException.class,
        () -> HandInPolicy.validateDeadline(LocalDateTime.of(2026, 3, 29, 2, 30)));
    assertThrows(IllegalArgumentException.class,
        () -> HandInPolicy.validateDeadline(LocalDateTime.of(2026, 10, 25, 2, 30)));
    assertDoesNotThrow(() -> HandInPolicy.validateDeadline(LocalDateTime.of(2026, 11, 30, 12, 0)));
  }
}
