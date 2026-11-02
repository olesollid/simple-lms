package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StaticPageTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction", 0);
  }

  @Test
  void createsPageWithBody() {
    StaticPage page = new StaticPage(module(), "Syllabus", 0, "Welcome to the course!");

    assertEquals("Welcome to the course!", page.getBody());
  }

  @Test
  void rejectsNullBody() {
    assertThrows(IllegalArgumentException.class, () -> new StaticPage(module(), "Syllabus", 0, null));
  }
}
