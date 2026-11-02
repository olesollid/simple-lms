package com._gods.canvas2;

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
    return new Module(course(), "Week 1: Introduction", 0);
  }

  @Test
  void createsContentWithValidFields() {
    Module module = module();
    Content content = new TestContent(module, "Syllabus", 0);

    assertEquals(module, content.getModule());
    assertEquals("Syllabus", content.getTitle());
    assertEquals(0, content.getPosition());
    assertNull(content.getVisibleFrom());
    assertNull(content.getVisibleUntil());
  }

  @Test
  void rejectsNullModule() {
    assertThrows(IllegalArgumentException.class, () -> new TestContent(null, "Syllabus", 0));
  }

  @Test
  void rejectsBlankTitle() {
    assertThrows(IllegalArgumentException.class, () -> new TestContent(module(), "  ", 0));
  }

  @Test
  void rejectsNegativePosition() {
    assertThrows(IllegalArgumentException.class, () -> new TestContent(module(), "Syllabus", -1));
  }

  @Test
  void rejectsVisibleUntilNotAfterVisibleFrom() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = from.minusMinutes(1);

    assertThrows(IllegalArgumentException.class,
        () -> new TestContent(module(), "Syllabus", 0, from, until));
  }

  @Test
  void isVisibleAtRespectsVisibilityWindow() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = LocalDateTime.of(2026, 1, 8, 8, 0);
    Content content = new TestContent(module(), "Syllabus", 0, from, until);

    assertFalse(content.isVisibleAt(from.minusMinutes(1)));
    assertTrue(content.isVisibleAt(from));
    assertTrue(content.isVisibleAt(until.minusMinutes(1)));
    assertFalse(content.isVisibleAt(until));
  }

  @Test
  void isVisibleAtWithNoWindowIsAlwaysVisible() {
    Content content = new TestContent(module(), "Syllabus", 0);

    assertTrue(content.isVisibleAt(LocalDateTime.now()));
  }
}
