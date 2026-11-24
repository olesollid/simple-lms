package com._gods.simplelms.grading;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.grading.QuizGradeStore;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.StudentInteraction;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.ContentRepository;
import com._gods.simplelms.repository.CourseRepository;
import com._gods.simplelms.repository.EnrollmentRepository;
import com._gods.simplelms.repository.ModuleRepository;
import com._gods.simplelms.repository.StudentInteractionRepository;
import com._gods.simplelms.repository.UserRepository;
import com._gods.simplelms.service.QuizService;
import com._gods.simplelms.service.QuizSubmissionService;
import com._gods.simplelms.web.dto.AnswerOptionRequest;
import com._gods.simplelms.web.dto.QuestionRequest;
import com._gods.simplelms.web.dto.QuizSubmissionRequest;

/**
 * Proves the real publish -> consume -> grade -> Redis pipeline against real infrastructure
 * (Testcontainers-managed RabbitMQ and Redis), not mocks. The "dev" profile is activated so
 * {@link com._gods.simplelms.messaging.SubmissionGradingListener}'s {@code @RabbitListener}
 * registers; without it the listener is absent and nothing would ever consume the event.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class QuizGradingPipelineIntegrationTests {

  @Container
  // RabbitMQ can take close to a minute to boot on a slow laptop or a busy CI runner, which is
  // right at Testcontainers' default limit, so give it more room before calling it a failure.
  static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:4")
      .withStartupTimeout(Duration.ofMinutes(3));

  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
    registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
    registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
    registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);

    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @Autowired
  private QuizService quizService;

  @Autowired
  private QuizSubmissionService quizSubmissionService;

  @Autowired
  private QuizGradeStore quizGradeStore;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private StudentInteractionRepository studentInteractionRepository;

  @Test
  void submittedQuizIsGradedAsynchronouslyAndStoredInRedis() throws InterruptedException {
    Course course = courseRepository.save(new Course("Pipeline Test", "desc", Term.AUTUMN, 2026));
    User teacher = userRepository.save(new User("pipeline-teacher@example.com", "Teacher"));
    enrollmentRepository.save(new Enrollment(teacher, course, Role.TEACHER));
    User student = userRepository.save(new User("pipeline-student@example.com", "Student"));
    enrollmentRepository.save(new Enrollment(student, course, Role.STUDENT));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = contentRepository.save(new Quiz(module, "Week 1 Quiz", 0, 1));

    quizService.addQuestion(quiz.getId(),
        new QuestionRequest("Which are prime?", null, List.of(
            new AnswerOptionRequest("2", true),
            new AnswerOptionRequest("4", false))),
        teacher.getEmail());
    var reloadedQuestion = quizService.getQuestionsWithAnswers(quiz.getId(), teacher.getEmail()).get(0);
    Long correctOptionId = reloadedQuestion.getOptions().stream()
        .filter(o -> o.getText().equals("2"))
        .findFirst().orElseThrow().getId();
    quizService.publishQuiz(quiz.getId(), teacher.getEmail());

    int attemptNumber = quizSubmissionService.submit(quiz.getId(),
        new QuizSubmissionRequest(Map.of(reloadedQuestion.getId(), List.of(correctOptionId))),
        student.getEmail());

    Optional<QuizGrade> grade = awaitGrade(quiz, student.getEmail());

    assertTrue(grade.isPresent());
    assertEquals(1, grade.get().correctQuestions());
    assertEquals(1, grade.get().totalQuestions());
    assertEquals(1.0, grade.get().score());
    assertEquals(1, attemptNumber);
    assertEquals(1, grade.get().attemptNumber());
    assertEquals(List.of(), grade.get().incorrectQuestionIds());

    Optional<StudentInteraction> interaction =
        studentInteractionRepository.findByUserAndContent(student, quiz);
    assertTrue(interaction.isPresent(), "submitting a quiz should mark it as interacted with");
    assertTrue(interaction.get().getCompletedAt() != null);
  }

  @Test
  void anOrderingAttemptIsGradedAndItsResultsFollowTheQuizsVisibilitySetting()
      throws InterruptedException {
    Course course = courseRepository.save(new Course("Ordering Test", "desc", Term.AUTUMN, 2026));
    User teacher = userRepository.save(new User("order-teacher@example.com", "Teacher"));
    enrollmentRepository.save(new Enrollment(teacher, course, Role.TEACHER));
    User student = userRepository.save(new User("order-student@example.com", "Student"));
    enrollmentRepository.save(new Enrollment(student, course, Role.STUDENT));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = contentRepository.save(new Quiz(module, "Ordering quiz", 0, 0));

    quizService.addQuestion(quiz.getId(), new QuestionRequest("Order the steps", "Plan first", List.of(
        new AnswerOptionRequest("Plan", false), new AnswerOptionRequest("Build", false),
        new AnswerOptionRequest("Test", false)), Question.Type.ORDERING), teacher.getEmail());
    var question = quizService.getQuestionsWithAnswers(quiz.getId(), teacher.getEmail()).get(0);
    List<Long> authored = question.getOptionsInOrder().stream().map(o -> o.getId()).toList();
    quizService.publishQuiz(quiz.getId(), teacher.getEmail());

    // By default results show as soon as an attempt is graded, hints included.
    quizSubmissionService.submit(quiz.getId(),
        new QuizSubmissionRequest(Map.of(question.getId(), authored.reversed())), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 1);
    var afterWrong = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(true, afterWrong.resultsVisible());
    assertEquals(1, afterWrong.attemptsGraded());
    assertEquals(0.0, afterWrong.best().score());
    assertEquals(Map.of(question.getId(), "Plan first"), afterWrong.hints());

    // The teacher holds the results back: the next attempt is graded but not shown.
    quizService.setShowResultsImmediately(quiz.getId(), false, teacher.getEmail());
    quizSubmissionService.submit(quiz.getId(),
        new QuizSubmissionRequest(Map.of(question.getId(), authored)), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 2);

    var hidden = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(false, hidden.resultsVisible());
    assertEquals(2, hidden.attemptsGraded());
    assertEquals(null, hidden.best());
    assertEquals(null, hidden.latest());
    assertEquals(Map.of(), hidden.hints());

    // Publishing the grades reveals them. Reload first: the local copy predates the changes above.
    Quiz current = quizService.getQuiz(quiz.getId());
    current.publishGrades();
    contentRepository.saveAndFlush(current);
    var visible = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(true, visible.resultsVisible());
    assertEquals(1.0, visible.best().score());
    assertEquals(2, visible.best().attemptNumber());
  }

  @Test
  void matchingAndFillInAnswersTravelThroughTheQueueAndAreGraded() throws InterruptedException {
    Course course = courseRepository.save(new Course("Types Test", "desc", Term.AUTUMN, 2026));
    User teacher = userRepository.save(new User("types-teacher@example.com", "Teacher"));
    enrollmentRepository.save(new Enrollment(teacher, course, Role.TEACHER));
    User student = userRepository.save(new User("types-student@example.com", "Student"));
    enrollmentRepository.save(new Enrollment(student, course, Role.STUDENT));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = contentRepository.save(new Quiz(module, "Types quiz", 0, 0));

    quizService.addQuestion(quiz.getId(), new QuestionRequest("Match the capitals", null, List.of(
        new AnswerOptionRequest("Norway", false, "Oslo"), new AnswerOptionRequest("France", false, "Paris"),
        new AnswerOptionRequest("Spain", false, "Madrid")), Question.Type.MATCHING), teacher.getEmail());
    quizService.addQuestion(quiz.getId(), new QuestionRequest("The capital of ___ is ___.", null, List.of(
        new AnswerOptionRequest("Norway|Norge", false), new AnswerOptionRequest("Oslo", false)),
        Question.Type.FILL_IN), teacher.getEmail());
    var questions = quizService.getQuestionsWithAnswers(quiz.getId(), teacher.getEmail());
    var matching = questions.get(0);
    var fillIn = questions.get(1);
    List<Long> pairIds = matching.getOptionsInOrder().stream().map(o -> o.getId()).toList();
    quizService.publishQuiz(quiz.getId(), teacher.getEmail());

    // Everything right, with the typed answers in different case and spacing.
    quizSubmissionService.submit(quiz.getId(), new QuizSubmissionRequest(
        Map.of(matching.getId(), pairIds),
        Map.of(fillIn.getId(), List.of("  norge ", "OSLO"))), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 1);
    var perfect = quizGradeStore.findLatest(quiz, student.getEmail()).orElseThrow();
    assertEquals(2, perfect.correctQuestions());
    assertEquals(List.of(), perfect.incorrectQuestionIds());

    // Two pairs swapped and a wrong blank: both questions are wrong.
    quizSubmissionService.submit(quiz.getId(), new QuizSubmissionRequest(
        Map.of(matching.getId(), List.of(pairIds.get(1), pairIds.get(0), pairIds.get(2))),
        Map.of(fillIn.getId(), List.of("Sweden", "Oslo"))), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 2);
    var wrong = quizGradeStore.findLatest(quiz, student.getEmail()).orElseThrow();
    assertEquals(0, wrong.correctQuestions());
    assertEquals(List.of(matching.getId(), fillIn.getId()), wrong.incorrectQuestionIds());
    // The best attempt still counts.
    assertEquals(1.0, quizGradeStore.find(quiz, student.getEmail()).orElseThrow().score());
  }

  private void awaitGraded(Quiz quiz, String studentEmail, int attempt) throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      if (quizGradeStore.findLatest(quiz, studentEmail)
          .map(grade -> grade.attemptNumber() >= attempt).orElse(false)) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Attempt " + attempt + " was not graded in time");
  }

  /** Grading happens asynchronously off a real broker, so poll instead of asserting immediately. */
  private Optional<QuizGrade> awaitGrade(Quiz quiz, String studentEmail) throws InterruptedException {
    for (int attempt = 0; attempt < 100; attempt++) {
      Optional<QuizGrade> grade = quizGradeStore.find(quiz, studentEmail);
      if (grade.isPresent()) {
        return grade;
      }
      Thread.sleep(100);
    }
    return Optional.empty();
  }
}
