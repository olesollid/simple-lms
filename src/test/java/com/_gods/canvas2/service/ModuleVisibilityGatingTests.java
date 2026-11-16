package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;

/**
 * Covers the "a dependent module is only visible once every content item in each module it
 * depends on has been interacted with" rule, which spans {@link ModuleService#isLocked},
 * {@link StudentInteractionService}, and the resulting block on {@link ContentService} reads.
 */
@DataJpaTest
@Import({ModuleService.class, ContentService.class, EnrollmentService.class, UserService.class,
    StudentInteractionService.class})
class ModuleVisibilityGatingTests {

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private ContentService contentService;

  @Autowired
  private StudentInteractionService studentInteractionService;

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
  void moduleWithNoDependenciesIsNeverLocked() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertFalse(moduleService.isLocked(module, student));
  }

  @Test
  void moduleIsLockedWhenDependencyHasUncompletedContent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Welcome!", null, null, teacher);
    week2 = moduleService.addDependency(week2.getId(), week1.getId(), teacher);

    assertTrue(moduleService.isLocked(week2, student));
  }

  @Test
  void moduleUnlocksOnceAllDependencyContentIsCompleted() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    StaticPage page = contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacher);
    week2 = moduleService.addDependency(week2.getId(), week1.getId(), teacher);

    studentInteractionService.markCompleted(page.getId(), student);

    assertFalse(moduleService.isLocked(week2, student));
  }

  @Test
  void moduleStaysLockedIfOnlySomeDependencyContentIsCompleted() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    StaticPage pageA = contentService.createStaticPage(week1.getId(), "A", 0, "a", null, null, teacher);
    contentService.createStaticPage(week1.getId(), "B", 1, "b", null, null, teacher);
    week2 = moduleService.addDependency(week2.getId(), week1.getId(), teacher);

    studentInteractionService.markCompleted(pageA.getId(), student);

    assertTrue(moduleService.isLocked(week2, student));
  }

  @Test
  void teacherIsNeverLockedOutOfAnyModule() {
    Course course = course();
    String teacher = teacherOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Welcome!", null, null, teacher);
    week2 = moduleService.addDependency(week2.getId(), week1.getId(), teacher);

    assertFalse(moduleService.isLocked(week2, teacher));
  }

  @Test
  void studentCannotSeeContentsOfALockedModule() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Welcome!", null, null, teacher);
    week2 = moduleService.addDependency(week2.getId(), week1.getId(), teacher);
    StaticPage lockedContent = contentService.createStaticPage(week2.getId(), "Locked", 0,
        "Shh", null, null, teacher);

    List<Content> contents = contentService.getContentsByModule(week2.getId(), student);

    assertTrue(contents.isEmpty());
    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(lockedContent.getId(), student));
  }
}
