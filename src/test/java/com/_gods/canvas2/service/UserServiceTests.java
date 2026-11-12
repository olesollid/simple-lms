package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;

@DataJpaTest
@Import(UserService.class)
class UserServiceTests {

  @Autowired
  private UserService userService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Test
  void createsAndRetrievesUser() {
    userService.createUser("student@example.com", "Student Name");

    User found = userService.getUserByEmail("student@example.com");

    assertEquals("Student Name", found.getName());
  }

  @Test
  void looksUpUserWithUnnormalizedEmail() {
    userService.createUser("student@example.com", "Student Name");

    User found = userService.getUserByEmail("  STUDENT@example.com ");

    assertEquals("student@example.com", found.getEmail());
  }

  @Test
  void retrievesAllUsers() {
    userService.createUser("a@example.com", "A");
    userService.createUser("b@example.com", "B");

    assertEquals(2, userService.getAllUsers().size());
  }

  @Test
  void rejectsDuplicateEmail() {
    userService.createUser("student@example.com", "Student Name");

    assertThrows(ConflictException.class,
        () -> userService.createUser("Student@Example.com", "Other Name"));
  }

  @Test
  void throwsWhenUserNotFound() {
    assertThrows(NotFoundException.class, () -> userService.getUserByEmail("missing@example.com"));
  }

  @Test
  void updatesUserName() {
    userService.createUser("student@example.com", "Student Name");

    userService.updateUserName("student@example.com", "New Name", "student@example.com");

    assertEquals("New Name", userService.getUserByEmail("student@example.com").getName());
  }

  @Test
  void rejectsUpdatingAnotherUsersName() {
    userService.createUser("student@example.com", "Student Name");
    userService.createUser("other@example.com", "Other Name");

    assertThrows(ForbiddenException.class,
        () -> userService.updateUserName("student@example.com", "New Name", "other@example.com"));
  }

  @Test
  void deletesUserWithNoDependents() {
    userService.createUser("student@example.com", "Student Name");

    userService.deleteUser("student@example.com", "student@example.com");

    assertThrows(NotFoundException.class, () -> userService.getUserByEmail("student@example.com"));
  }

  @Test
  void rejectsDeletingAnotherUser() {
    userService.createUser("student@example.com", "Student Name");
    userService.createUser("other@example.com", "Other Name");

    assertThrows(ForbiddenException.class,
        () -> userService.deleteUser("student@example.com", "other@example.com"));
  }

  @Test
  void rejectsDeletingUserWithEnrollments() {
    User user = userService.createUser("student@example.com", "Student Name");
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));

    assertThrows(ConflictException.class,
        () -> userService.deleteUser("student@example.com", "student@example.com"));
  }
}
