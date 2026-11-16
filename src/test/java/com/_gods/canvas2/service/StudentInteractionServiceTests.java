package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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
import com._gods.canvas2.repository.StudentInteractionRepository;
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
  private ModuleService moduleService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private StudentInteractionRepository studentInteractionRepository;

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

  private StaticPage pageIn(Course course, String teacher) {
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!", null, null,
        teacher);
  }

  @Test
  void marksContentCompletedForTheFirstTime() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    StaticPage page = pageIn(course, teacher);

    StudentInteraction interaction = studentInteractionService.markCompleted(page.getId(), student);

    assertNotNull(interaction.getCompletedAt());
  }

  @Test
  void markingTheSameContentCompletedTwiceUpdatesTheSameInteraction() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    StaticPage page = pageIn(course, teacher);

    StudentInteraction first = studentInteractionService.markCompleted(page.getId(), student);
    StudentInteraction second = studentInteractionService.markCompleted(page.getId(), student);

    assertNotNull(second.getCompletedAt());
    assertEquals(first.getId(), second.getId());
  }

  @Test
  void rejectsMarkingCompletedWhenNotEnrolled() {
    Course course = course();
    String teacher = teacherOf(course);
    StaticPage page = pageIn(course, teacher);

    assertThrows(ForbiddenException.class,
        () -> studentInteractionService.markCompleted(page.getId(), "stranger@example.com"));
  }

  @Test
  void throwsWhenMarkingMissingContentCompleted() {
    Course course = course();
    String teacher = teacherOf(course);
    pageIn(course, teacher);

    assertThrows(ContentNotFoundException.class,
        () -> studentInteractionService.markCompleted(999L, teacher));
  }

  @Test
  void teacherCanAlsoMarkContentCompleted() {
    Course course = course();
    String teacher = teacherOf(course);
    StaticPage page = pageIn(course, teacher);

    StudentInteraction interaction = studentInteractionService.markCompleted(page.getId(), teacher);

    assertNotNull(interaction.getCompletedAt());
  }

  @Test
  void studentCannotMarkContentCompletedInALockedModule() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    StaticPage week1Page = pageIn(course, teacher);
    Module week2 = moduleRepository.save(new Module(course, "Week 2"));
    week2 = moduleService.addDependency(week2.getId(), week1Page.getModule().getId(), teacher);
    StaticPage week2Page = contentService.createStaticPage(week2.getId(), "Locked", 0, "Shh",
        null, null, teacher);

    assertThrows(ContentNotFoundException.class,
        () -> studentInteractionService.markCompleted(week2Page.getId(), student));
  }

  @Test
  void studentCanMarkContentCompletedOnceItsModuleUnlocks() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    StaticPage week1Page = pageIn(course, teacher);
    Module week2 = moduleRepository.save(new Module(course, "Week 2"));
    week2 = moduleService.addDependency(week2.getId(), week1Page.getModule().getId(), teacher);
    StaticPage week2Page = contentService.createStaticPage(week2.getId(), "Advanced", 0, "Hi",
        null, null, teacher);
    studentInteractionService.markCompleted(week1Page.getId(), student);

    StudentInteraction interaction = studentInteractionService.markCompleted(week2Page.getId(), student);

    assertNotNull(interaction.getCompletedAt());
  }

  @Test
  void teacherCanMarkContentCompletedEvenInALockedModule() {
    Course course = course();
    String teacher = teacherOf(course);
    StaticPage week1Page = pageIn(course, teacher);
    Module week2 = moduleRepository.save(new Module(course, "Week 2"));
    week2 = moduleService.addDependency(week2.getId(), week1Page.getModule().getId(), teacher);
    StaticPage week2Page = contentService.createStaticPage(week2.getId(), "Locked", 0, "Shh",
        null, null, teacher);

    StudentInteraction interaction = studentInteractionService.markCompleted(week2Page.getId(),
        teacher);

    assertNotNull(interaction.getCompletedAt());
  }

  @Test
  void studentCannotMarkContentCompletedOutsideItsVisibilityWindow() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    StaticPage page = pageIn(course, teacher);
    StaticPage futurePage = contentService.createStaticPage(page.getModule().getId(), "Not yet",
        1, "Shh", java.time.LocalDateTime.now().plusDays(1), null, teacher);

    assertThrows(ContentNotFoundException.class,
        () -> studentInteractionService.markCompleted(futurePage.getId(), student));
  }

  @Test
  void databaseRejectsADuplicateInteractionForTheSameUserAndContent() {
    Course course = course();
    String teacher = teacherOf(course);
    String studentEmail = studentOf(course);
    StaticPage page = pageIn(course, teacher);
    User student = userRepository.findById(studentEmail).orElseThrow();
    studentInteractionRepository.save(new StudentInteraction(student, page));

    assertThrows(DataIntegrityViolationException.class,
        () -> studentInteractionRepository.saveAndFlush(new StudentInteraction(student, page)));
  }
}
