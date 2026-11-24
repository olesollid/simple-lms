package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;

class EnrollmentTests {

  private User user() {
    return new User("student@example.com", "Student Name");
  }

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  @Test
  void createsEnrollmentWithValidFields() {
    User user = user();
    Course course = course();
    Enrollment enrollment = new Enrollment(user, course, Role.STUDENT);

    assertEquals(user, enrollment.getUser());
    assertEquals(course, enrollment.getCourse());
    assertEquals(Role.STUDENT, enrollment.getRole());
  }

  @Test
  void rejectsNullFields() {
    assertThrows(IllegalArgumentException.class, () -> new Enrollment(null, course(), Role.STUDENT));
    assertThrows(IllegalArgumentException.class, () -> new Enrollment(user(), null, Role.STUDENT));
    assertThrows(IllegalArgumentException.class, () -> new Enrollment(user(), course(), null));
  }

  @Test
  void changesRole() {
    Enrollment enrollment = new Enrollment(user(), course(), Role.STUDENT);

    enrollment.setRole(Role.TEACHER);

    assertEquals(Role.TEACHER, enrollment.getRole());
  }

  @Test
  void setRoleRejectsNull() {
    Enrollment enrollment = new Enrollment(user(), course(), Role.STUDENT);

    assertThrows(IllegalArgumentException.class, () -> enrollment.setRole(null));
  }
}
