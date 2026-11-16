package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({ContentService.class, EnrollmentService.class, UserService.class})
class ContentServiceTests {

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

  private Module module() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    teacherEmail = email;
    return moduleRepository.save(new Module(course, "Week 1"));
  }

  private String studentOf(Module module) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, module.getCourse(), Role.STUDENT));
    return email;
  }

  @Test
  void createsStaticPage() {
    Module module = module();

    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);

    assertEquals("Syllabus", page.getTitle());
    assertEquals("Welcome!", page.getBody());
    assertEquals(module.getId(), page.getModule().getId());
  }

  @Test
  void rejectsCreatingStaticPageByStudent() {
    Module module = module();
    String student = studentOf(module);

    assertThrows(ForbiddenException.class,
        () -> contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!", null,
            null, student));
  }

  @Test
  void throwsWhenCreatingStaticPageForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.createStaticPage(999L, "Syllabus", 0, "Welcome!", null, null,
            "nobody@example.com"));
  }

  @Test
  void createsQuiz() {
    Module module = module();

    Quiz quiz = contentService.createQuiz(module.getId(), "Week 1 Quiz", 0, 1, false, null,
        teacherEmail);

    assertEquals("Week 1 Quiz", quiz.getTitle());
    assertEquals(1, quiz.getMaxAttempts());
    assertEquals(module.getId(), quiz.getModule().getId());
  }

  @Test
  void rejectsCreatingQuizByStudent() {
    Module module = module();
    String student = studentOf(module);

    assertThrows(ForbiddenException.class,
        () -> contentService.createQuiz(module.getId(), "Week 1 Quiz", 0, 1, false, null,
            student));
  }

  @Test
  void throwsWhenCreatingQuizForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.createQuiz(999L, "Week 1 Quiz", 0, 1, false, null,
            "nobody@example.com"));
  }

  @Test
  void retrievesContentsByModuleInPositionOrder() {
    Module module = module();
    contentService.createStaticPage(module.getId(), "Second", 1, "B", null, null, teacherEmail);
    contentService.createStaticPage(module.getId(), "First", 0, "A", null, null, teacherEmail);

    List<Content> contents = contentService.getContentsByModule(module.getId(), teacherEmail);

    assertEquals(List.of("First", "Second"), contents.stream().map(Content::getTitle).toList());
  }

  @Test
  void rejectsGetContentsByModuleWhenNotEnrolled() {
    Module module = module();

    assertThrows(ForbiddenException.class,
        () -> contentService.getContentsByModule(module.getId(), "stranger@example.com"));
  }

  @Test
  void studentCanGetContentsByModule() {
    Module module = module();
    String student = studentOf(module);
    contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!", null, null,
        teacherEmail);

    assertEquals(1, contentService.getContentsByModule(module.getId(), student).size());
  }

  @Test
  void throwsWhenRetrievingContentsForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.getContentsByModule(999L, "nobody@example.com"));
  }

  @Test
  void throwsWhenContentNotFound() {
    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(999L, "nobody@example.com"));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.updateContentMetadata(999L, "x", 0, null, null, "nobody@example.com"));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.updateStaticPageBody(999L, "x", "nobody@example.com"));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.deleteContent(999L, "nobody@example.com"));
  }

  @Test
  void updatesContentMetadata() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = LocalDateTime.of(2026, 1, 8, 8, 0);

    Content updated = contentService.updateContentMetadata(page.getId(), "Updated", 3, from, until,
        teacherEmail);

    assertEquals("Updated", updated.getTitle());
    assertEquals(3, updated.getPosition());
    assertEquals(from, updated.getVisibleFrom());
    assertEquals(until, updated.getVisibleUntil());
  }

  @Test
  void rejectsUpdatingContentMetadataByStudent() {
    Module module = module();
    String student = studentOf(module);
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);

    assertThrows(ForbiddenException.class,
        () -> contentService.updateContentMetadata(page.getId(), "Hacked", 0, null, null, student));
  }

  @Test
  void rejectsInvalidVisibilityWindowOnUpdate() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = from.minusMinutes(1);

    assertThrows(IllegalArgumentException.class,
        () -> contentService.updateContentMetadata(page.getId(), "Syllabus", 0, from, until,
            teacherEmail));
  }

  @Test
  void updatesStaticPageBody() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);

    StaticPage updated = contentService.updateStaticPageBody(page.getId(), "Updated body",
        teacherEmail);

    assertEquals("Updated body", updated.getBody());
  }

  @Test
  void deletesContent() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);

    contentService.deleteContent(page.getId(), teacherEmail);

    assertTrue(contentService.getContentsByModule(module.getId(), teacherEmail).isEmpty());
    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(page.getId(), teacherEmail));
  }

  @Test
  void rejectsDeletingContentByStudent() {
    Module module = module();
    String student = studentOf(module);
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null, teacherEmail);

    assertThrows(ForbiddenException.class,
        () -> contentService.deleteContent(page.getId(), student));
  }

  @Test
  void studentCannotSeeContentBeforeItsVisibleFromDate() {
    Module module = module();
    String student = studentOf(module);
    contentService.createStaticPage(module.getId(), "Future", 0, "Not yet",
        LocalDateTime.now().plusDays(1), null, teacherEmail);

    assertTrue(contentService.getContentsByModule(module.getId(), student).isEmpty());
  }

  @Test
  void studentCannotSeeContentAfterItsVisibleUntilDate() {
    Module module = module();
    String student = studentOf(module);
    contentService.createStaticPage(module.getId(), "Past", 0, "Expired",
        LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), teacherEmail);

    assertTrue(contentService.getContentsByModule(module.getId(), student).isEmpty());
  }

  @Test
  void studentSeesContentWithinItsVisibilityWindow() {
    Module module = module();
    String student = studentOf(module);
    contentService.createStaticPage(module.getId(), "Current", 0, "Visible now",
        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), teacherEmail);

    assertEquals(1, contentService.getContentsByModule(module.getId(), student).size());
  }

  @Test
  void teacherSeesContentOutsideItsVisibilityWindow() {
    Module module = module();
    contentService.createStaticPage(module.getId(), "Future", 0, "Not yet",
        LocalDateTime.now().plusDays(1), null, teacherEmail);

    assertEquals(1, contentService.getContentsByModule(module.getId(), teacherEmail).size());
  }

  @Test
  void studentGettingInvisibleContentByIdGetsNotFound() {
    Module module = module();
    String student = studentOf(module);
    StaticPage page = contentService.createStaticPage(module.getId(), "Future", 0, "Not yet",
        LocalDateTime.now().plusDays(1), null, teacherEmail);

    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(page.getId(), student));
  }

  @Test
  void teacherCanGetInvisibleContentById() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Future", 0, "Not yet",
        LocalDateTime.now().plusDays(1), null, teacherEmail);

    Content content = contentService.getContentById(page.getId(), teacherEmail);

    assertEquals(page.getId(), content.getId());
  }
}
