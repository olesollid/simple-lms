package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com._gods.canvas2.grading.QuizGrade;
import com._gods.canvas2.grading.QuizGradeStore;
import com._gods.canvas2.messaging.SubmissionCreatedEvent;
import com._gods.canvas2.messaging.SubmissionPublisher;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.HandInPolicy;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.UserRepository;
import com._gods.canvas2.web.dto.AnswerOptionRequest;
import com._gods.canvas2.web.dto.QuestionRequest;
import com._gods.canvas2.web.dto.QuizAttemptsResponse;
import com._gods.canvas2.web.dto.QuizSubmissionRequest;

/** Redis (attempt counter, grades) and RabbitMQ (grading queue) are mocked; the rules are real. */
@DataJpaTest
@Import({QuizSubmissionService.class, QuizService.class, EnrollmentService.class,
    UserService.class, ModuleService.class, StudentInteractionService.class})
class QuizSubmissionServiceTests {

  @Autowired
  private QuizSubmissionService quizSubmissionService;

  @Autowired
  private QuizService quizService;

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @MockitoBean
  private QuizGradeStore quizGradeStore;

  @MockitoBean
  private SubmissionPublisher submissionPublisher;

  private static int userCounter = 0;
  private static final QuizSubmissionRequest ANSWERS = new QuizSubmissionRequest(Map.of());

  private Course course;
  private String teacher;

  private Quiz quiz(int maxAttempts) {
    course = courseRepository.save(new Course("Intro", "desc", Term.AUTUMN, 2026));
    teacher = enrol(Role.TEACHER);
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return quizIn(module, maxAttempts);
  }

  private Quiz quizIn(Module module, int maxAttempts) {
    Quiz quiz = contentRepository.save(new Quiz(module, "Quiz", 0, maxAttempts, false));
    quizService.addQuestion(quiz.getId(), new QuestionRequest("Q", null, List.of(
        new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", false))), teacher);
    return quiz;
  }

  private String enrol(Role role) {
    String email = role.name().toLowerCase() + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Someone"));
    enrollmentRepository.save(new Enrollment(user, course, role));
    return email;
  }

  @Test
  void submittingClaimsAnAttemptAndSendsItsNumberForGrading() {
    Quiz quiz = quiz(3);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    when(quizGradeStore.reserveAttempt(any(Quiz.class), eq(student))).thenReturn(Optional.of(2));

    int attempt = quizSubmissionService.submit(quiz.getId(), ANSWERS, student);

    assertEquals(2, attempt);
    ArgumentCaptor<SubmissionCreatedEvent> event = ArgumentCaptor.forClass(SubmissionCreatedEvent.class);
    verify(submissionPublisher).publish(event.capture());
    assertEquals(2, event.getValue().attemptNumber());
    assertEquals(student, event.getValue().studentEmail());
  }

  @Test
  void rejectsASubmissionOnceAllAttemptsAreUsed() {
    Quiz quiz = quiz(1);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    when(quizGradeStore.reserveAttempt(any(Quiz.class), eq(student))).thenReturn(Optional.empty());

    assertThrows(ConflictException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
    verify(submissionPublisher, never()).publish(any());
  }

  @Test
  void givesTheAttemptBackWhenTheSubmissionCannotBeQueued() {
    Quiz quiz = quiz(1);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    when(quizGradeStore.reserveAttempt(any(Quiz.class), eq(student))).thenReturn(Optional.of(1));
    doThrow(new IllegalStateException("broker down")).when(submissionPublisher).publish(any());

    assertThrows(IllegalStateException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
    verify(quizGradeStore).releaseAttempt(any(Quiz.class), eq(student));
  }

  @Test
  void studentsCannotSubmitToADraftQuiz() {
    Quiz quiz = quiz(1);
    String student = enrol(Role.STUDENT);

    assertThrows(QuizNotFoundException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
    verify(quizGradeStore, never()).reserveAttempt(any(), anyString());
  }

  @Test
  void studentsCannotSubmitToAQuizInALockedModule() {
    Quiz week1Quiz = quiz(1);
    quizService.publishQuiz(week1Quiz.getId(), teacher);
    Module week2 = moduleRepository.save(new Module(course, "Week 2"));
    week2 = moduleService.addDependency(week2.getId(), week1Quiz.getModule().getId(), teacher);
    Quiz week2Quiz = quizIn(week2, 1);
    quizService.publishQuiz(week2Quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);

    assertThrows(QuizNotFoundException.class,
        () -> quizSubmissionService.submit(week2Quiz.getId(), ANSWERS, student));
  }

  @Test
  void studentsCannotSubmitOutsideTheVisibilityWindow() {
    Quiz quiz = quiz(1);
    quiz.updateMetadata("Quiz", 0, LocalDateTime.now().plusDays(1), null);
    contentRepository.saveAndFlush(quiz);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);

    assertThrows(QuizNotFoundException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
  }

  @Test
  void rejectsAttemptsAfterTheDeadlineWithoutUsingOne() {
    Quiz quiz = quiz(3);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    quiz.updateDeadline(LocalDateTime.now(HandInPolicy.ZONE).minusHours(1));
    contentRepository.saveAndFlush(quiz);

    assertThrows(ForbiddenException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
    verify(quizGradeStore, never()).reserveAttempt(any(), anyString());
    assertEquals(true, quizSubmissionService.getOwnAttempts(quiz.getId(), student).closed());
  }

  @Test
  void acceptsAttemptsBeforeTheDeadline() {
    Quiz quiz = quiz(3);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    quiz.updateDeadline(LocalDateTime.now(HandInPolicy.ZONE).plusDays(1));
    contentRepository.saveAndFlush(quiz);
    when(quizGradeStore.reserveAttempt(any(Quiz.class), eq(student))).thenReturn(Optional.of(1));

    assertEquals(1, quizSubmissionService.submit(quiz.getId(), ANSWERS, student));
    assertEquals(false, quizSubmissionService.getOwnAttempts(quiz.getId(), student).closed());
  }

  @Test
  void revealsHintsOnlyForQuestionsTheLatestAttemptGotWrong() {
    Quiz quiz = quiz(3);
    var wrong = quizService.addQuestion(quiz.getId(), new QuestionRequest("Wrong one", "Think again",
        List.of(new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", false))), teacher);
    var right = quizService.addQuestion(quiz.getId(), new QuestionRequest("Right one", "Not needed",
        List.of(new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", false))), teacher);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);

    assertEquals(Map.of(), quizSubmissionService.getOwnAttempts(quiz.getId(), student).hints());

    QuizGrade latest = new QuizGrade(quiz.getId(), student, 1, 2, 3, 0.67, List.of(wrong.getId()),
        LocalDateTime.now());
    when(quizGradeStore.findLatest(any(Quiz.class), eq(student))).thenReturn(Optional.of(latest));

    Map<Long, String> hints = quizSubmissionService.getOwnAttempts(quiz.getId(), student).hints();
    assertEquals(Map.of(wrong.getId(), "Think again"), hints);
    assertEquals(null, hints.get(right.getId()));
  }

  @Test
  void hidesScoresAndHintsUntilTheTeacherPublishesWhenResultsAreNotImmediate() {
    Quiz quiz = quiz(3);
    var question = quizService.addQuestion(quiz.getId(), new QuestionRequest("Tricky", "Think again",
        List.of(new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", false))), teacher);
    quizService.publishQuiz(quiz.getId(), teacher);
    quizService.setShowResultsImmediately(quiz.getId(), false, teacher);
    String student = enrol(Role.STUDENT);
    QuizGrade graded = new QuizGrade(quiz.getId(), student, 1, 1, 2, 0.5, List.of(question.getId()),
        LocalDateTime.now());
    when(quizGradeStore.attemptsUsed(any(Quiz.class), eq(student))).thenReturn(1);
    when(quizGradeStore.find(any(Quiz.class), eq(student))).thenReturn(Optional.of(graded));
    when(quizGradeStore.findLatest(any(Quiz.class), eq(student))).thenReturn(Optional.of(graded));

    QuizAttemptsResponse hidden = quizSubmissionService.getOwnAttempts(quiz.getId(), student);
    assertEquals(false, hidden.resultsVisible());
    assertEquals(1, hidden.attemptsUsed());
    assertEquals(null, hidden.best());
    assertEquals(null, hidden.latest());
    assertEquals(Map.of(), hidden.hints());

    Quiz stored = quizService.getQuiz(quiz.getId());
    stored.publishGrades();
    contentRepository.saveAndFlush(stored);

    QuizAttemptsResponse visible = quizSubmissionService.getOwnAttempts(quiz.getId(), student);
    assertEquals(true, visible.resultsVisible());
    assertEquals(graded, visible.best());
    assertEquals(Map.of(question.getId(), "Think again"), visible.hints());
  }

  @Test
  void onlyTeachersChooseWhenResultsAreShown() {
    Quiz quiz = quiz(1);
    String student = enrol(Role.STUDENT);

    assertThrows(ForbiddenException.class,
        () -> quizService.setShowResultsImmediately(quiz.getId(), false, student));
    assertEquals(false, quizService.setShowResultsImmediately(quiz.getId(), false, teacher)
        .isShowResultsImmediately());
  }

  @Test
  void rejectsSubmissionsFromPeopleOutsideTheCourse() {
    Quiz quiz = quiz(1);
    quizService.publishQuiz(quiz.getId(), teacher);

    assertThrows(ForbiddenException.class,
        () -> quizSubmissionService.submit(quiz.getId(), ANSWERS, "stranger@example.com"));
  }

  @Test
  void reportsTheStudentsOwnAttemptsAndGrades() {
    Quiz quiz = quiz(0);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);
    QuizGrade best = new QuizGrade(quiz.getId(), student, 1, 1, 1, 1.0, List.of(), LocalDateTime.now());
    QuizGrade latest = new QuizGrade(quiz.getId(), student, 2, 0, 1, 0.0, List.of(7L), LocalDateTime.now());
    when(quizGradeStore.attemptsUsed(any(Quiz.class), eq(student))).thenReturn(2);
    when(quizGradeStore.find(any(Quiz.class), eq(student))).thenReturn(Optional.of(best));
    when(quizGradeStore.findLatest(any(Quiz.class), eq(student))).thenReturn(Optional.of(latest));

    QuizAttemptsResponse attempts = quizSubmissionService.getOwnAttempts(quiz.getId(), student);

    assertEquals(2, attempts.attemptsUsed());
    assertEquals(0, attempts.maxAttempts());
    assertEquals(null, attempts.deadline());
    assertEquals(false, attempts.closed());
    assertEquals(best, attempts.best());
    assertEquals(latest, attempts.latest());
  }

  @Test
  void teacherSeesEveryStudentsAttemptsAndBestGradeSortedByName() {
    Quiz quiz = quiz(3);
    quizService.publishQuiz(quiz.getId(), teacher);
    String zara = enrolNamed("Zara");
    String adam = enrolNamed("adam");
    QuizGrade zaraBest = new QuizGrade(quiz.getId(), zara, 2, 1, 1, 1.0, List.of(), LocalDateTime.now());
    when(quizGradeStore.attemptsUsed(any(Quiz.class), eq(zara))).thenReturn(2);
    when(quizGradeStore.find(any(Quiz.class), eq(zara))).thenReturn(Optional.of(zaraBest));
    when(quizGradeStore.find(any(Quiz.class), eq(adam))).thenReturn(Optional.empty());

    var results = quizSubmissionService.getResults(quiz.getId(), teacher);

    assertEquals(List.of("adam", "Zara"), results.stream().map(r -> r.name()).toList());
    assertEquals(0, results.get(0).attemptsUsed());
    assertEquals(null, results.get(0).best());
    assertEquals(2, results.get(1).attemptsUsed());
    assertEquals(zaraBest, results.get(1).best());
  }

  @Test
  void studentsCannotSeeTheClassResults() {
    Quiz quiz = quiz(3);
    quizService.publishQuiz(quiz.getId(), teacher);
    String student = enrol(Role.STUDENT);

    assertThrows(ForbiddenException.class, () -> quizSubmissionService.getResults(quiz.getId(), student));
  }

  private String enrolNamed(String name) {
    String email = name.toLowerCase() + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, name));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));
    return email;
  }

  @Test
  void rejectsSubmittingToContentThatIsNotAQuiz() {
    course = courseRepository.save(new Course("Intro", "desc", Term.AUTUMN, 2026));
    String student = enrol(Role.STUDENT);
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    StaticPage page = contentRepository.save(new StaticPage(module, "Page", 0, "Hi"));

    assertThrows(QuizNotFoundException.class,
        () -> quizSubmissionService.submit(page.getId(), ANSWERS, student));
  }
}
