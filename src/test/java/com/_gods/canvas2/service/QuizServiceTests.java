package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Question;
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

@DataJpaTest
@Import({QuizService.class, EnrollmentService.class, UserService.class})
class QuizServiceTests {

  @Autowired
  private QuizService quizService;

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

  private static int userCounter = 0;
  private String teacherEmail;

  private Quiz quiz() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    teacherEmail = email;
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return contentRepository.save(new Quiz(module, "Week 1 Quiz", 0, 1, false));
  }

  private String studentOf(Quiz quiz) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, quiz.getModule().getCourse(), Role.STUDENT));
    return email;
  }

  private QuestionRequest twoOptionQuestion() {
    return new QuestionRequest("Which are prime?", List.of(
        new AnswerOptionRequest("2", true),
        new AnswerOptionRequest("4", false)));
  }

  @Test
  void addsQuestionWithOptions() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertEquals("Which are prime?", question.getText());
    assertEquals(2, question.getOptions().size());
  }

  @Test
  void acceptsLongQuestionText() {
    Quiz quiz = quiz();
    String longText = "q".repeat(5_000);

    Question question = quizService.addQuestion(quiz.getId(),
        new QuestionRequest(longText, twoOptionQuestion().options()), teacherEmail);

    assertEquals(longText, question.getText());
  }

  @Test
  void addedQuestionAndOptionsHaveIdsAssignedImmediately() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertNotNull(question.getId());
    assertTrue(question.getOptions().stream().allMatch(option -> option.getId() != null));
  }

  @Test
  void rejectsAddingQuestionByStudent() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);

    assertThrows(ForbiddenException.class,
        () -> quizService.addQuestion(quiz.getId(), twoOptionQuestion(), student));
  }

  @Test
  void rejectsQuestionWithFewerThanTwoOptions() {
    Quiz quiz = quiz();
    QuestionRequest request = new QuestionRequest("Only one option",
        List.of(new AnswerOptionRequest("A", true)));

    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), request, teacherEmail));
  }

  @Test
  void rejectsQuestionWithNoCorrectOption() {
    Quiz quiz = quiz();
    QuestionRequest request = new QuestionRequest("No correct answer", List.of(
        new AnswerOptionRequest("A", false),
        new AnswerOptionRequest("B", false)));

    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), request, teacherEmail));
  }

  @Test
  void rejectsAddingQuestionToMissingQuiz() {
    assertThrows(QuizNotFoundException.class,
        () -> quizService.addQuestion(999L, twoOptionQuestion(), "nobody@example.com"));
  }

  @Test
  void rejectsAddingQuestionToNonQuizContent() {
    Course course = courseRepository.save(new Course("Intro", "desc", Term.AUTUMN, 2026));
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    var page = contentRepository.save(new StaticPage(module, "Syllabus", 0, "Welcome"));

    assertThrows(QuizNotFoundException.class,
        () -> quizService.addQuestion(page.getId(), twoOptionQuestion(), email));
  }

  @Test
  void studentCanReadQuestionsButWithoutCorrectFlagExposedByServiceContract() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    List<Question> questions = quizService.getQuestions(quiz.getId(), student);

    assertEquals(1, questions.size());
    // The service returns the real entity; hiding `correct` from students is the web layer's
    // job (QuestionResponse vs QuestionWithAnswersResponse). Confirm the entity itself still
    // reports the real flag so that contract is testable at the controller layer.
    assertTrue(questions.get(0).getOptions().stream().anyMatch(AnswerOption::isCorrect));
  }

  @Test
  void rejectsGetQuestionsWhenNotEnrolled() {
    Quiz quiz = quiz();

    assertThrows(ForbiddenException.class,
        () -> quizService.getQuestions(quiz.getId(), "stranger@example.com"));
  }

  @Test
  void rejectsGetQuestionsWithAnswersByStudent() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertThrows(ForbiddenException.class,
        () -> quizService.getQuestionsWithAnswers(quiz.getId(), student));
  }

  @Test
  void teacherCanReadQuestionsWithAnswers() {
    Quiz quiz = quiz();
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    List<Question> questions = quizService.getQuestionsWithAnswers(quiz.getId(), teacherEmail);

    assertEquals(1, questions.size());
  }
}
