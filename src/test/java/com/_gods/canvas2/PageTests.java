package com._gods.canvas2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction", 0);
  }

  @Test
  void createsPageWithBody() {
    Page page = new Page(module(), "Syllabus", 0, "Welcome to the course!");

    assertEquals("Welcome to the course!", page.getBody());
  }

  @Test
  void rejectsNullBody() {
    assertThrows(IllegalArgumentException.class, () -> new Page(module(), "Syllabus", 0, null));
  }
}
