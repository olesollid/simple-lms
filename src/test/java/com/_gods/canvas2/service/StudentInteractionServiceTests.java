package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({StudentInteractionService.class, ContentService.class, ModuleService.class,
    EnrollmentService.class, UserService.class})
class StudentInteractionServiceTests {

  @Autowired
  private StudentInteractionService studentInteractionService;

  @Autowired
  private ContentService contentService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  private static int userCounter = 0;
  private String teacherEmail;

  private StaticPage page() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    teacherEmail = email;
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!", null, null,
        teacherEmail);
  }

  private String studentOf(StaticPage page) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, page.getModule().getCourse(), Role.STUDENT));
    return email;
  }

  @Test
  void marksContentCompletedForTheFirstTime() {
    StaticPage page = page();
    String student = studentOf(page);

    StudentInteraction interaction = studentInteractionService.markCompleted(page.getId(), student);

    assertNotNull(interaction.getCompletedAt());
  }

  @Test
  void markingTheSameContentCompletedTwiceUpdatesTheSameInteraction() {
    StaticPage page = page();
    String student = studentOf(page);

    StudentInteraction first = studentInteractionService.markCompleted(page.getId(), student);
    StudentInteraction second = studentInteractionService.markCompleted(page.getId(), student);

    assertNotNull(second.getCompletedAt());
    assertEquals(first.getId(), second.getId());
  }

  @Test
  void rejectsMarkingCompletedWhenNotEnrolled() {
    StaticPage page = page();

    assertThrows(ForbiddenException.class,
        () -> studentInteractionService.markCompleted(page.getId(), "stranger@example.com"));
  }

  @Test
  void throwsWhenMarkingMissingContentCompleted() {
    page();

    assertThrows(ContentNotFoundException.class,
        () -> studentInteractionService.markCompleted(999L, teacherEmail));
  }

  @Test
  void teacherCanAlsoMarkContentCompleted() {
    StaticPage page = page();

    StudentInteraction interaction = studentInteractionService.markCompleted(page.getId(),
        teacherEmail);

    assertNotNull(interaction.getCompletedAt());
  }
}
