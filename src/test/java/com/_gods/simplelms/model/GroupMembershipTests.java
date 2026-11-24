package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.GroupMembership;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;

class GroupMembershipTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  private User user(String email) {
    return new User(email, "Test User");
  }

  @Test
  void createsGroupMembershipWithValidFields() {
    Course course = course();
    User user = user("student@example.com");
    Group group = new Group(course, "Group 1");

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
    User user = user("student@example.com");

    assertThrows(IllegalArgumentException.class, () -> new GroupMembership(user, null));
  }

  @Test
  void groupSupportsMultipleMembers() {
    Course course = course();
    Group group = new Group(course, "Group 1");
    User userOne = user("student1@example.com");
    User userTwo = user("student2@example.com");

    GroupMembership membershipOne = new GroupMembership(userOne, group);
    GroupMembership membershipTwo = new GroupMembership(userTwo, group);

    assertEquals(group, membershipOne.getGroup());
    assertEquals(group, membershipTwo.getGroup());
  }

  @Test
  void userCanBelongToMultipleGroups() {
    Course course = course();
    User user = user("student@example.com");
    Group groupOne = new Group(course, "Group 1");
    Group groupTwo = new Group(course, "Group 2");

    GroupMembership membershipOne = new GroupMembership(user, groupOne);
    GroupMembership membershipTwo = new GroupMembership(user, groupTwo);

    assertEquals(groupOne, membershipOne.getGroup());
    assertEquals(groupTwo, membershipTwo.getGroup());
  }
}
