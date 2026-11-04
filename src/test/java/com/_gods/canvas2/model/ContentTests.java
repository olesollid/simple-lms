package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ContentTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  private Module module() {
    return new Module(course(), "Week 1: Introduction");
  }

  @Test
  void createsContentWithValidFields() {
    Module module = module();
    Content content = new StaticPage(module, "Syllabus", 0, "Welcome!");

    assertEquals(module, content.getModule());
    assertEquals("Syllabus", content.getTitle());
    assertEquals(0, content.getPosition());
    assertNull(content.getVisibleFrom());
    assertNull(content.getVisibleUntil());
  }

  @Test
  void rejectsNullModule() {
    assertThrows(IllegalArgumentException.class, () -> new StaticPage(null, "Syllabus", 0, "Welcome!"));
  }

  @Test
  void rejectsBlankTitle() {
    assertThrows(IllegalArgumentException.class, () -> new StaticPage(module(), "  ", 0, "Welcome!"));
  }

  @Test
  void rejectsNegativePosition() {
    assertThrows(IllegalArgumentException.class, () -> new StaticPage(module(), "Syllabus", -1, "Welcome!"));
  }

  @Test
  void rejectsVisibleUntilNotAfterVisibleFrom() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = from.minusMinutes(1);

    assertThrows(IllegalArgumentException.class,
        () -> new StaticPage(module(), "Syllabus", 0, "Welcome!", from, until));
  }

  @Test
  void isVisibleAtRespectsVisibilityWindow() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = LocalDateTime.of(2026, 1, 8, 8, 0);
    Content content = new StaticPage(module(), "Syllabus", 0, "Welcome!", from, until);

    assertFalse(content.isVisibleAt(from.minusMinutes(1)));
    assertTrue(content.isVisibleAt(from));
    assertTrue(content.isVisibleAt(until.minusMinutes(1)));
    assertFalse(content.isVisibleAt(until));
  }

  @Test
  void isVisibleAtWithNoWindowIsAlwaysVisible() {
    Content content = new StaticPage(module(), "Syllabus", 0, "Welcome!");

    assertTrue(content.isVisibleAt(LocalDateTime.now()));
  }
}
