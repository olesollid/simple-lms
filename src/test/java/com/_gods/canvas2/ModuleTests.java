package com._gods.canvas2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModuleTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  @Test
  void createsModuleWithValidFields() {
    Course course = course();
    Module module = new Module(course, "Week 1: Introduction", 0);

    assertEquals(course, module.getCourse());
    assertEquals("Week 1: Introduction", module.getTitle());
    assertEquals(0, module.getPosition());
    assertNull(module.getRequiredModule());
  }

  @Test
  void rejectsNullCourse() {
    assertThrows(IllegalArgumentException.class, () -> new Module(null, "Week 1", 0));
  }

  @Test
  void rejectsBlankTitle() {
    assertThrows(IllegalArgumentException.class, () -> new Module(course(), "  ", 0));
  }

  @Test
  void rejectsNegativePosition() {
    assertThrows(IllegalArgumentException.class, () -> new Module(course(), "Week 1", -1));
  }

  @Test
  void rejectsRequiredModuleFromDifferentCourse() {
    Course course = course();
    Course otherCourse = course();
    Module requiredModule = new Module(otherCourse, "Week 1", 0);

    assertThrows(IllegalArgumentException.class,
        () -> new Module(course, "Week 2", 1, requiredModule));
  }

  @Test
  void acceptsRequiredModuleFromSameCourse() {
    Course course = course();
    Module requiredModule = new Module(course, "Week 1", 0);
    Module module = new Module(course, "Week 2", 1, requiredModule);

    assertEquals(requiredModule, module.getRequiredModule());
  }
}
