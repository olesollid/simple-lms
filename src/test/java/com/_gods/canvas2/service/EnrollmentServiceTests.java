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
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.GroupMembershipRepository;
import com._gods.canvas2.repository.GroupRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({EnrollmentService.class, UserService.class})
class EnrollmentServiceTests {

  private static final String EMAIL = "student@example.com";
  private static final String TEACHER_EMAIL = "teacher@example.com";

  @Autowired
  private EnrollmentService enrollmentService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private GroupMembershipRepository groupMembershipRepository;

  private Course course;

  @BeforeEach
  void setUp() {
    course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    userRepository.save(new User(EMAIL, "Student Name"));
    User teacher = userRepository.save(new User(TEACHER_EMAIL, "Teacher Name"));
    enrollmentRepository.save(new Enrollment(teacher, course, Role.TEACHER));
  }

  @Test
  void enrollsUserInCourse() {
    Enrollment enrollment = enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT,
        TEACHER_EMAIL);

    assertEquals(Role.STUDENT, enrollment.getRole());
    assertEquals(EMAIL, enrollmentService.getEnrollment(course.getId(), EMAIL).getUser().getEmail());
  }

  @Test
  void rejectsEnrollingByNonTeacher() {
    assertThrows(ForbiddenException.class,
        () -> enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, EMAIL));
  }

  @Test
  void rejectsEnrollingAsTeacherOfADifferentCourse() {
    Course other = courseRepository.save(new Course("Other Course", "desc", Term.SPRING, 2027));

    assertThrows(ForbiddenException.class,
        () -> enrollmentService.enroll(other.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL));
  }

  @Test
  void rejectsDuplicateEnrollment() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);

    assertThrows(ConflictException.class,
        () -> enrollmentService.enroll(course.getId(), EMAIL, Role.TEACHER, TEACHER_EMAIL));
  }

  @Test
  void throwsWhenCourseNotFound() {
    assertThrows(CourseNotFoundException.class,
        () -> enrollmentService.enroll(999L, EMAIL, Role.STUDENT, TEACHER_EMAIL));
  }

  @Test
  void throwsWhenUserNotFound() {
    assertThrows(NotFoundException.class,
        () -> enrollmentService.enroll(course.getId(), "missing@example.com", Role.STUDENT,
            TEACHER_EMAIL));
  }

  @Test
  void throwsWhenNotEnrolled() {
    assertThrows(NotFoundException.class,
        () -> enrollmentService.getEnrollment(course.getId(), EMAIL));
  }

  @Test
  void filtersEnrollmentsByRole() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);

    assertEquals(2, enrollmentService.getEnrollmentsByCourse(course.getId()).size());
    assertEquals(1, enrollmentService.getEnrollmentsByCourse(course.getId(), Role.TEACHER).size());
  }

  @Test
  void retrievesEnrollmentsByUser() {
    Course other = courseRepository.save(new Course("Other Course", "desc", Term.SPRING, 2027));
    User otherTeacher = userRepository.save(new User("other-teacher@example.com", "Other Teacher"));
    enrollmentRepository.save(new Enrollment(otherTeacher, other, Role.TEACHER));
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);
    enrollmentService.enroll(other.getId(), EMAIL, Role.STUDENT, "other-teacher@example.com");

    assertEquals(2, enrollmentService.getEnrollmentsByUser(EMAIL).size());
  }

  @Test
  void rolesAreCourseSpecific() {
    Course other = courseRepository.save(new Course("Other Course", "desc", Term.SPRING, 2027));
    User otherTeacher = userRepository.save(new User("other-teacher@example.com", "Other Teacher"));
    enrollmentRepository.save(new Enrollment(otherTeacher, other, Role.TEACHER));
    enrollmentService.enroll(other.getId(), EMAIL, Role.STUDENT, "other-teacher@example.com");

    assertTrue(enrollmentService.hasRole(course.getId(), TEACHER_EMAIL, Role.TEACHER));
    assertFalse(enrollmentService.hasRole(other.getId(), TEACHER_EMAIL, Role.TEACHER));
  }

  @Test
  void hasRoleIsFalseWhenNotEnrolled() {
    assertFalse(enrollmentService.hasRole(course.getId(), EMAIL, Role.STUDENT));
  }

  @Test
  void hasRoleIsFalseForUnknownUser() {
    assertFalse(enrollmentService.hasRole(course.getId(), "ghost@example.com", Role.STUDENT));
  }

  @Test
  void changesRole() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);

    enrollmentService.changeRole(course.getId(), EMAIL, Role.TEACHER, TEACHER_EMAIL);

    assertEquals(Role.TEACHER, enrollmentService.getEnrollment(course.getId(), EMAIL).getRole());
  }

  @Test
  void rejectsChangingRoleByNonTeacher() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);
    User other = userRepository.save(new User("other@example.com", "Other Name"));
    enrollmentRepository.save(new Enrollment(other, course, Role.STUDENT));

    assertThrows(ForbiddenException.class,
        () -> enrollmentService.changeRole(course.getId(), EMAIL, Role.TEACHER, "other@example.com"));
  }

  @Test
  void rejectsSoleTeacherDowngradingSelf() {
    assertThrows(ConflictException.class,
        () -> enrollmentService.changeRole(course.getId(), TEACHER_EMAIL, Role.STUDENT,
            TEACHER_EMAIL));
  }

  @Test
  void allowsTeacherDowngradeWhenAnotherTeacherRemains() {
    User secondTeacher = userRepository.save(new User("teacher2@example.com", "Teacher Two"));
    enrollmentRepository.save(new Enrollment(secondTeacher, course, Role.TEACHER));

    enrollmentService.changeRole(course.getId(), TEACHER_EMAIL, Role.STUDENT, TEACHER_EMAIL);

    assertEquals(Role.STUDENT, enrollmentService.getEnrollment(course.getId(), TEACHER_EMAIL).getRole());
  }

  @Test
  void unenrollRemovesEnrollmentAndGroupMemberships() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);
    User user = userRepository.findById(EMAIL).orElseThrow();
    Group group = groupRepository.save(new Group(course, "Group 1"));
    groupMembershipRepository.save(new GroupMembership(user, group));

    enrollmentService.unenroll(course.getId(), EMAIL, EMAIL);

    assertThrows(NotFoundException.class,
        () -> enrollmentService.getEnrollment(course.getId(), EMAIL));
    assertTrue(groupMembershipRepository.findByUser(user).isEmpty());
  }

  @Test
  void rejectsUnenrollingAnotherStudentByNonTeacher() {
    enrollmentService.enroll(course.getId(), EMAIL, Role.STUDENT, TEACHER_EMAIL);
    User other = userRepository.save(new User("other@example.com", "Other Name"));
    enrollmentRepository.save(new Enrollment(other, course, Role.STUDENT));

    assertThrows(ForbiddenException.class,
        () -> enrollmentService.unenroll(course.getId(), EMAIL, "other@example.com"));
  }

  @Test
  void rejectsSoleTeacherUnenrollingSelf() {
    assertThrows(ConflictException.class,
        () -> enrollmentService.unenroll(course.getId(), TEACHER_EMAIL, TEACHER_EMAIL));
  }

  @Test
  void allowsTeacherUnenrollWhenAnotherTeacherRemains() {
    User secondTeacher = userRepository.save(new User("teacher2@example.com", "Teacher Two"));
    enrollmentRepository.save(new Enrollment(secondTeacher, course, Role.TEACHER));

    enrollmentService.unenroll(course.getId(), TEACHER_EMAIL, TEACHER_EMAIL);

    assertThrows(NotFoundException.class,
        () -> enrollmentService.getEnrollment(course.getId(), TEACHER_EMAIL));
  }
}
