package com._gods.canvas2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserTests {

  @Test
  void normalizesEmailAndTrimsName() {
    User user = new User("  Student@Example.COM ", "  Student Name ");

    assertEquals("student@example.com", user.getEmail());
    assertEquals("Student Name", user.getName());
  }

  @Test
  void rejectsBlankEmail() {
    assertThrows(IllegalArgumentException.class, () -> new User("  ", "Student Name"));
  }

  @Test
  void rejectsInvalidEmail() {
    assertThrows(IllegalArgumentException.class, () -> new User("no-at-sign", "Name"));
    assertThrows(IllegalArgumentException.class, () -> new User("@example.com", "Name"));
    assertThrows(IllegalArgumentException.class, () -> new User("a@b@c.com", "Name"));
    assertThrows(IllegalArgumentException.class, () -> new User("student@", "Name"));
  }

  @Test
  void rejectsBlankName() {
    assertThrows(IllegalArgumentException.class, () -> new User("student@example.com", " "));
  }

  @Test
  void setNameRejectsBlankName() {
    User user = new User("student@example.com", "Student Name");

    assertThrows(IllegalArgumentException.class, () -> user.setName(null));
    assertEquals("Student Name", user.getName());
  }
}
