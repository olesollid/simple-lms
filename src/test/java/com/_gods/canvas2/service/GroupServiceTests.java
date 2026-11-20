package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.GroupMembership;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({GroupService.class, EnrollmentService.class, UserService.class})
class GroupServiceTests {

  @Autowired
  private GroupService groupService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  private static int userCounter = 0;

  private Course course() {
    return courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
  }

  private String teacherOf(Course course) {
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    return email;
  }

  private String studentOf(Course course) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));
    return email;
  }

  @Test
  void teacherCreatesAGroup() {
    Course course = course();
    String teacher = teacherOf(course);

    Group group = groupService.createGroup(course.getId(), "Team A", teacher);

    assertEquals("Team A", group.getName());
    assertEquals(course.getId(), group.getCourse().getId());
  }

  @Test
  void rejectsCreatingGroupByStudent() {
    Course course = course();
    String student = studentOf(course);

    assertThrows(ForbiddenException.class,
        () -> groupService.createGroup(course.getId(), "Team A", student));
  }

  @Test
  void teacherAddsAStudentToAGroup() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);

    GroupMembership membership = groupService.addMember(group.getId(), student, teacher);

    assertEquals(student, membership.getUser().getEmail());
    assertEquals(group.getId(), membership.getGroup().getId());
  }

  @Test
  void rejectsAddingAStudentAlreadyInAGroupInTheSameCourse() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group groupA = groupService.createGroup(course.getId(), "Team A", teacher);
    Group groupB = groupService.createGroup(course.getId(), "Team B", teacher);
    groupService.addMember(groupA.getId(), student, teacher);

    assertThrows(ConflictException.class,
        () -> groupService.addMember(groupB.getId(), student, teacher));
  }

  @Test
  void rejectsAddingATeacherAsAMember() {
    Course course = course();
    String teacher = teacherOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);

    assertThrows(ConflictException.class,
        () -> groupService.addMember(group.getId(), teacher, teacher));
  }

  @Test
  void rejectsAddingMembersByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);

    assertThrows(ForbiddenException.class,
        () -> groupService.addMember(group.getId(), student, student));
  }

  @Test
  void teacherRemovesAMember() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), student, teacher);

    groupService.removeMember(group.getId(), student, teacher);

    assertTrue(groupService.getMembers(group.getId(), teacher).isEmpty());
  }

  @Test
  void studentCanSeeTheirOwnGroup() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), student, teacher);

    Group ownGroup = groupService.getOwnGroup(course.getId(), student);

    assertEquals(group.getId(), ownGroup.getId());
  }

  @Test
  void throwsWhenStudentHasNoGroup() {
    Course course = course();
    String student = studentOf(course);

    assertThrows(NotFoundException.class, () -> groupService.getOwnGroup(course.getId(), student));
  }

  @Test
  void listsGroupsInACourse() {
    Course course = course();
    String teacher = teacherOf(course);
    groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.createGroup(course.getId(), "Team B", teacher);

    List<Group> groups = groupService.getGroupsByCourse(course.getId(), teacher);

    assertEquals(2, groups.size());
  }

  @Test
  void teacherDeletesAGroupAndItsMemberships() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), student, teacher);

    groupService.deleteGroup(group.getId(), teacher);

    assertTrue(groupService.getGroupsByCourse(course.getId(), teacher).isEmpty());
    // The freed student can be placed in a new group.
    Group other = groupService.createGroup(course.getId(), "Team B", teacher);
    groupService.addMember(other.getId(), student, teacher);
  }

  @Test
  void rejectsDeletingAGroupByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);

    assertThrows(ForbiddenException.class, () -> groupService.deleteGroup(group.getId(), student));
  }

  @Test
  void rejectsDeletingAnUnknownGroup() {
    Course course = course();
    String teacher = teacherOf(course);

    assertThrows(GroupNotFoundException.class, () -> groupService.deleteGroup(9999L, teacher));
  }
}
