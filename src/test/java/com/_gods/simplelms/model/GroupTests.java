package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.Term;

class GroupTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  @Test
  void createsGroupWithValidFields() {
    Course course = course();
    Group group = new Group(course, "Group 1");

    assertEquals(course, group.getCourse());
    assertEquals("Group 1", group.getName());
  }

  @Test
  void rejectsNullCourse() {
    assertThrows(IllegalArgumentException.class, () -> new Group(null, "Group 1"));
  }

  @Test
  void rejectsBlankName() {
    assertThrows(IllegalArgumentException.class, () -> new Group(course(), "  "));
  }
}
