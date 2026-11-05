package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
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
import com._gods.canvas2.repository.GroupMembershipRepository;
import com._gods.canvas2.repository.GroupRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({EnrollmentService.class, UserService.class})
class EnrollmentServiceTests {

  private static final String EMAIL = "student@example.com";

  @Autowired
  private EnrollmentService enrollmentService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private GroupMembershipRepository groupMembershipRepository;

  private Course course;

  @BeforeEach
  void setUp() {
    course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    userRepository.save(new User(EMAIL, "Student Name"));
  }

  @Test
  void enrollsUserInCourse() {
    Enrollment enrollment = enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);

    assertEquals(Role.STUDENT, enrollment.getRole());
    assertEquals(EMAIL, enrollmentService.getEnrollment(course.getId(), EMAIL).getUser().getEmail());
  }

  @Test
  void rejectsDuplicateEnrollment() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);

    assertThrows(ConflictException.class,
        () -> enrollmentService.enroll(course.getId(), EMAIL, Role.TEACHER));
  }

  @Test
  void throwsWhenCourseNotFound() {
    assertThrows(CourseNotFoundException.class,
        () -> enrollmentService.enroll(999L, EMAIL, Role.STUDENT));
  }

  @Test
  void throwsWhenUserNotFound() {
    assertThrows(NotFoundException.class,
        () -> enrollmentService.enroll(course.getId(), "missing@example.com", Role.STUDENT));
  }

  @Test
  void throwsWhenNotEnrolled() {
    assertThrows(NotFoundException.class,
        () -> enrollmentService.getEnrollment(course.getId(), EMAIL));
  }

  @Test
  void filtersEnrollmentsByRole() {
    userRepository.save(new User("teacher@example.com", "Teacher Name"));
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);
    enrollmentService.enroll(course.getId(), "teacher@example.com", Role.TEACHER);

    assertEquals(2, enrollmentService.getEnrollmentsByCourse(course.getId()).size());
    assertEquals(1, enrollmentService.getEnrollmentsByCourse(course.getId(), Role.TEACHER).size());
  }

  @Test
  void retrievesEnrollmentsByUser() {
    Course other = courseRepository.save(new Course("Other Course", "desc", Term.SPRING, 2027));
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);
    enrollmentService.enroll(other.getId(), EMAIL, Role.TEACHER);

    assertEquals(2, enrollmentService.getEnrollmentsByUser(EMAIL).size());
  }

  @Test
  void rolesAreCourseSpecific() {
    Course other = courseRepository.save(new Course("Other Course", "desc", Term.SPRING, 2027));
    enrollmentService.enroll(course.getId(), EMAIL, Role.TEACHER);
    enrollmentService.enroll(other.getId(), EMAIL, Role.STUDENT);

    assertTrue(enrollmentService.hasRole(course.getId(), EMAIL, Role.TEACHER));
    assertFalse(enrollmentService.hasRole(other.getId(), EMAIL, Role.TEACHER));
  }

  @Test
  void hasRoleIsFalseWhenNotEnrolled() {
    assertFalse(enrollmentService.hasRole(course.getId(), EMAIL, Role.STUDENT));
  }

  @Test
  void changesRole() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);

    enrollmentService.changeRole(course.getId(), EMAIL, Role.TEACHER);

    assertEquals(Role.TEACHER, enrollmentService.getEnrollment(course.getId(), EMAIL).getRole());
  }

  @Test
  void unenrollRemovesEnrollmentAndGroupMemberships() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT);
    User user = userRepository.findById(EMAIL).orElseThrow();
    Group group = groupRepository.save(new Group(course, "Group 1"));
    groupMembershipRepository.save(new GroupMembership(user, group));

    enrollmentService.unenroll(course.getId(), EMAIL);

    assertThrows(NotFoundException.class,
        () -> enrollmentService.getEnrollment(course.getId(), EMAIL));
    assertTrue(groupMembershipRepository.findByUser(user).isEmpty());
  }
}
