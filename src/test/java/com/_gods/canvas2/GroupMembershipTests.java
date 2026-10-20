package com._gods.canvas2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GroupMembershipTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  private User user() {
    return new User("student@example.com", "Student Name");
  }

  @Test
  void createsMembershipWithValidFields() {
    User user = user();
    Group group = new Group(course(), "Group 1");
    GroupMembership membership = new GroupMembership(user, group);

    assertEquals(user, membership.getUser());
    assertEquals(group, membership.getGroup());
  }

  @Test
  void rejectsNullUser() {
    Group group = new Group(course(), "Group 1");
    assertThrows(IllegalArgumentException.class, () -> new GroupMembership(null, group));
  }

  @Test
  void rejectsNullGroup() {
    assertThrows(IllegalArgumentException.class, () -> new GroupMembership(user(), null));
  }
}
