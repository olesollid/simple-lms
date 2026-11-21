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

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;
import com._gods.canvas2.repository.SubmissionRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({ContentService.class, ModuleService.class, EnrollmentService.class, UserService.class,
    StudentInteractionService.class})
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

  @Autowired
  private SubmissionRepository submissionRepository;

  @Autowired
  private StudentInteractionRepository studentInteractionRepository;

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
    return studentUserOf(module).getEmail();
  }

  private User studentUserOf(Module module) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, module.getCourse(), Role.STUDENT));
    return user;
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
  void teacherCanSetAndClearAQuizDeadline() {
    Module module = module();
    Quiz quiz = contentService.createQuiz(module.getId(), "Week 1 Quiz", 0, 1, false, null,
        teacherEmail);
    LocalDateTime deadline = LocalDateTime.of(2026, 12, 1, 12, 0);

    Quiz updated = (Quiz) contentService.updateSubmissionPolicy(quiz.getId(), deadline, true,
        teacherEmail);
    assertEquals(deadline, updated.getDeadline());

    Quiz cleared = (Quiz) contentService.updateSubmissionPolicy(quiz.getId(), null, false,
        teacherEmail);
    assertEquals(null, cleared.getDeadline());
  }

  @Test
  void studentCannotChangeAQuizDeadline() {
    Module module = module();
    Quiz quiz = contentService.createQuiz(module.getId(), "Week 1 Quiz", 0, 1, false, null,
        teacherEmail);
    String student = studentOf(module);

    assertThrows(ForbiddenException.class, () -> contentService.updateSubmissionPolicy(
        quiz.getId(), LocalDateTime.of(2026, 12, 1, 12, 0), false, student));
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
  void createsAssignment() {
    Module module = module();

    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0,
        "Write 500 words", null, false, false, teacherEmail);

    assertEquals("Essay", assignment.getTitle());
    assertEquals("Write 500 words", assignment.getInstructions());
    assertEquals(module.getId(), assignment.getModule().getId());
  }

  @Test
  void acceptsLongAssignmentInstructions() {
    Module module = module();
    String instructions = "i".repeat(5_000);

    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0,
        instructions, null, false, false, teacherEmail);

    assertEquals(instructions, assignment.getInstructions());
  }

  @Test
  void rejectsCreatingAssignmentByStudent() {
    Module module = module();
    String student = studentOf(module);

    assertThrows(ForbiddenException.class,
        () -> contentService.createAssignment(module.getId(), "Essay", 0, "Write 500 words",
            null, false, false, student));
  }

  @Test
  void throwsWhenCreatingAssignmentForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.createAssignment(999L, "Essay", 0, "Write 500 words", null, false,
            false, "nobody@example.com"));
  }

  @Test
  void createsCodingExercise() {
    Module module = module();

    CodingExercise codingExercise = contentService.createCodingExercise(module.getId(),
        "Fix the bug", 0, "https://example.com/repo", null, false, false, teacherEmail);

    assertEquals("Fix the bug", codingExercise.getTitle());
    assertEquals("https://example.com/repo", codingExercise.getRepositoryUrl());
    assertEquals(module.getId(), codingExercise.getModule().getId());
  }

  @Test
  void rejectsCreatingCodingExerciseByStudent() {
    Module module = module();
    String student = studentOf(module);

    assertThrows(ForbiddenException.class,
        () -> contentService.createCodingExercise(module.getId(), "Fix the bug", 0,
            "https://example.com/repo", null, false, false, student));
  }

  @Test
  void throwsWhenCreatingCodingExerciseForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.createCodingExercise(999L, "Fix the bug", 0,
            "https://example.com/repo", null, false, false, "nobody@example.com"));
  }

  @Test
  void newContentGoesAtTheEndOfItsModuleWhateverPositionWasRequested() {
    Module module = module();
    contentService.createStaticPage(module.getId(), "First", 0, "A", null, null, teacherEmail);
    contentService.createQuiz(module.getId(), "Second", 0, 1, false, null, teacherEmail);
    contentService.createAssignment(module.getId(), "Third", 0, "Do it", null, false, false,
        teacherEmail);

    List<Content> contents = contentService.getContentsByModule(module.getId(), teacherEmail);

    assertEquals(List.of("First", "Second", "Third"), contents.stream().map(Content::getTitle).toList());
    assertEquals(List.of(0, 1, 2), contents.stream().map(Content::getPosition).toList());
  }

  @Test
  void teacherReordersAModulesContent() {
    Module module = module();
    StaticPage a = contentService.createStaticPage(module.getId(), "A", 0, "A", null, null, teacherEmail);
    StaticPage b = contentService.createStaticPage(module.getId(), "B", 0, "B", null, null, teacherEmail);
    StaticPage c = contentService.createStaticPage(module.getId(), "C", 0, "C", null, null, teacherEmail);

    contentService.reorderContents(module.getId(), List.of(c.getId(), a.getId(), b.getId()), teacherEmail);

    assertEquals(List.of("C", "A", "B"), contentService.getContentsByModule(module.getId(), teacherEmail)
        .stream().map(Content::getTitle).toList());
  }

  @Test
  void rejectsAReorderThatDoesNotListEveryItemExactlyOnce() {
    Module module = module();
    StaticPage a = contentService.createStaticPage(module.getId(), "A", 0, "A", null, null, teacherEmail);
    StaticPage b = contentService.createStaticPage(module.getId(), "B", 0, "B", null, null, teacherEmail);

    assertThrows(IllegalArgumentException.class,
        () -> contentService.reorderContents(module.getId(), List.of(a.getId()), teacherEmail));
    assertThrows(IllegalArgumentException.class,
        () -> contentService.reorderContents(module.getId(), List.of(a.getId(), a.getId()), teacherEmail));
    assertThrows(IllegalArgumentException.class, () -> contentService.reorderContents(module.getId(),
        List.of(a.getId(), b.getId(), 999L), teacherEmail));
  }

  @Test
  void studentCannotReorderContent() {
    Module module = module();
    StaticPage a = contentService.createStaticPage(module.getId(), "A", 0, "A", null, null, teacherEmail);
    String student = studentOf(module);

    assertThrows(ForbiddenException.class,
        () -> contentService.reorderContents(module.getId(), List.of(a.getId()), student));
  }

  @Test
  void teacherEditsTheTextOfEachContentType() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Page", 0, "Old", null, null, teacherEmail);
    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0, "Old",
        null, false, false, teacherEmail);
    CodingExercise exercise = contentService.createCodingExercise(module.getId(), "Code", 0,
        "https://example.com/old", null, false, false, teacherEmail);

    assertEquals("New body", ((StaticPage) contentService.updateText(page.getId(), "New body", teacherEmail)).getBody());
    assertEquals("New instructions",
        ((Assignment) contentService.updateText(assignment.getId(), "New instructions", teacherEmail)).getInstructions());
    assertEquals("https://example.com/new",
        ((CodingExercise) contentService.updateText(exercise.getId(), " https://example.com/new ", teacherEmail)).getRepositoryUrl());
  }

  @Test
  void rejectsBlankInstructionsAndTextForQuizzes() {
    Module module = module();
    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0, "Old",
        null, false, false, teacherEmail);
    Quiz quiz = contentService.createQuiz(module.getId(), "Quiz", 0, 1, false, null, teacherEmail);

    assertThrows(IllegalArgumentException.class,
        () -> contentService.updateText(assignment.getId(), "  ", teacherEmail));
    assertThrows(IllegalArgumentException.class,
        () -> contentService.updateText(quiz.getId(), "text", teacherEmail));
  }

  @Test
  void studentCannotEditContentText() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Page", 0, "Old", null, null, teacherEmail);
    String student = studentOf(module);

    assertThrows(ForbiddenException.class, () -> contentService.updateText(page.getId(), "Hacked", student));
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
  void deletingContentAlsoDeletesItsSubmissionsAndInteractions() {
    Module module = module();
    User student = studentUserOf(module);
    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0,
        "Write something", null, false, false, teacherEmail);
    submissionRepository.save(new Submission(student, assignment, "My essay"));
    studentInteractionRepository.save(new StudentInteraction(student, assignment));

    contentService.deleteContent(assignment.getId(), teacherEmail);

    assertTrue(submissionRepository.findByContent(assignment).isEmpty());
    assertTrue(studentInteractionRepository.findByContent(assignment).isEmpty());
    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(assignment.getId(), teacherEmail));
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
