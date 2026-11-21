package com._gods.canvas2.grading;

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

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;
import com._gods.canvas2.repository.UserRepository;
import com._gods.canvas2.service.QuizService;
import com._gods.canvas2.service.QuizSubmissionService;
import com._gods.canvas2.web.dto.AnswerOptionRequest;
import com._gods.canvas2.web.dto.QuestionRequest;
import com._gods.canvas2.web.dto.QuizSubmissionRequest;

/**
 * Proves the real publish -> consume -> grade -> Redis pipeline against real infrastructure
 * (Testcontainers-managed RabbitMQ and Redis), not mocks. The "dev" profile is activated so
 * {@link com._gods.canvas2.messaging.SubmissionGradingListener}'s {@code @RabbitListener}
 * registers; without it the listener is absent and nothing would ever consume the event.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class QuizGradingPipelineIntegrationTests {

  @Container
  static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:4");

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
    Quiz quiz = contentRepository.save(new Quiz(module, "Week 1 Quiz", 0, 1, false));

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
  void anOrderingAttemptIsGradedButStaysHiddenUntilTheTeacherPublishesTheResults()
      throws InterruptedException {
    Course course = courseRepository.save(new Course("Ordering Test", "desc", Term.AUTUMN, 2026));
    User teacher = userRepository.save(new User("order-teacher@example.com", "Teacher"));
    enrollmentRepository.save(new Enrollment(teacher, course, Role.TEACHER));
    User student = userRepository.save(new User("order-student@example.com", "Student"));
    enrollmentRepository.save(new Enrollment(student, course, Role.STUDENT));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = contentRepository.save(new Quiz(module, "Ordering quiz", 0, 0, false));

    quizService.addQuestion(quiz.getId(), new QuestionRequest("Order the steps", "Plan first", List.of(
        new AnswerOptionRequest("Plan", false), new AnswerOptionRequest("Build", false),
        new AnswerOptionRequest("Test", false)), Question.Type.ORDERING), teacher.getEmail());
    quizService.updateSettings(quiz.getId(), null, true, teacher.getEmail());
    var question = quizService.getQuestionsWithAnswers(quiz.getId(), teacher.getEmail()).get(0);
    List<Long> authored = question.getOptions().stream().map(o -> o.getId()).toList();
    quizService.publishQuiz(quiz.getId(), teacher.getEmail());

    // A wrong order first: hints arrive right away, the grade does not.
    quizSubmissionService.submit(quiz.getId(),
        new QuizSubmissionRequest(Map.of(question.getId(), authored.reversed())), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 1);
    var afterWrong = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(1, afterWrong.attemptsGraded());
    assertEquals(false, afterWrong.resultsPublished());
    assertEquals(null, afterWrong.best());
    assertEquals(Map.of(question.getId(), "Plan first"), afterWrong.hints());

    quizSubmissionService.submit(quiz.getId(),
        new QuizSubmissionRequest(Map.of(question.getId(), authored)), student.getEmail());
    awaitGraded(quiz, student.getEmail(), 2);

    var hidden = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(2, hidden.attemptsGraded());
    assertEquals(null, hidden.best());
    assertEquals(null, hidden.latest());

    // Reload first: the local copy predates publishQuiz and would overwrite the published flag.
    Quiz current = quizService.getQuiz(quiz.getId());
    current.publishGrades();
    contentRepository.saveAndFlush(current);
    var visible = quizSubmissionService.getOwnAttempts(quiz.getId(), student.getEmail());
    assertEquals(true, visible.resultsPublished());
    assertEquals(1.0, visible.best().score());
    assertEquals(2, visible.best().attemptNumber());
    assertEquals(Map.of(), visible.hints());
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
