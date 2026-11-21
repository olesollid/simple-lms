package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
@Import({QuizService.class, EnrollmentService.class, UserService.class, ModuleService.class,
    StudentInteractionService.class})
class QuizServiceTests {

  @Autowired
  private QuizService quizService;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TestEntityManager entityManager;

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

  @Test
  void eachQuizKeepsItsOwnGradeKeyThroughSaveAndReload() {
    Quiz first = quiz();
    Quiz second = quiz();
    String firstKey = first.getGradeKey();
    entityManager.flush();
    entityManager.clear();

    Quiz reloaded = quizService.getQuiz(first.getId());

    assertEquals(firstKey, reloaded.getGradeKey());
    assertEquals(36, firstKey.length());
    assertNotEquals(firstKey, quizService.getQuiz(second.getId()).getGradeKey());
  }

  @Test
  void draftQuizQuestionsAreHiddenFromStudentsUntilPublished() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertThrows(QuizNotFoundException.class, () -> quizService.getQuestions(quiz.getId(), student));
    assertEquals(1, quizService.getQuestions(quiz.getId(), teacherEmail).size());

    quizService.publishQuiz(quiz.getId(), teacherEmail);

    assertEquals(1, quizService.getQuestions(quiz.getId(), student).size());
  }

  @Test
  void rejectsPublishingAQuizWithoutQuestions() {
    Quiz quiz = quiz();

    assertThrows(ConflictException.class, () -> quizService.publishQuiz(quiz.getId(), teacherEmail));
  }

  @Test
  void rejectsPublishingByStudent() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertThrows(ForbiddenException.class, () -> quizService.publishQuiz(quiz.getId(), student));
  }

  @Test
  void questionsAreFrozenOnceTheQuizIsPublished() {
    Quiz quiz = quiz();
    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);
    quizService.publishQuiz(quiz.getId(), teacherEmail);

    assertThrows(ConflictException.class,
        () -> quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail));
    assertThrows(ConflictException.class, () -> quizService.updateQuestion(quiz.getId(),
        question.getId(), twoOptionQuestion(), teacherEmail));
    assertThrows(ConflictException.class,
        () -> quizService.deleteQuestion(quiz.getId(), question.getId(), teacherEmail));
  }

  @Test
  void updatesQuestionTextHintAndOptionsWhileDraft() {
    Quiz quiz = quiz();
    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    Question updated = quizService.updateQuestion(quiz.getId(), question.getId(),
        new QuestionRequest("Which is even?", "Even numbers divide by 2", List.of(
            new AnswerOptionRequest("3", false),
            new AnswerOptionRequest("6", true),
            new AnswerOptionRequest("9", false))), teacherEmail);

    assertEquals("Which is even?", updated.getText());
    assertEquals("Even numbers divide by 2", updated.getHint());
    List<Question> questions = quizService.getQuestionsWithAnswers(quiz.getId(), teacherEmail);
    assertEquals(3, questions.get(0).getOptions().size());
    assertEquals(List.of("6"), questions.get(0).getOptions().stream()
        .filter(AnswerOption::isCorrect).map(AnswerOption::getText).toList());
  }

  @Test
  void blankHintIsStoredAsNoHint() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(),
        new QuestionRequest("Q", "   ", twoOptionQuestion().options()), teacherEmail);

    assertEquals(null, question.getHint());
  }

  @Test
  void deletingAQuestionRenumbersTheRest() {
    Quiz quiz = quiz();
    Question first = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);
    quizService.addQuestion(quiz.getId(),
        new QuestionRequest("Second", null, twoOptionQuestion().options()), teacherEmail);

    quizService.deleteQuestion(quiz.getId(), first.getId(), teacherEmail);

    List<Question> questions = quizService.getQuestionsWithAnswers(quiz.getId(), teacherEmail);
    assertEquals(1, questions.size());
    assertEquals("Second", questions.get(0).getText());
    assertEquals(0, questions.get(0).getPosition());
  }

  @Test
  void rejectsEditingAQuestionFromAnotherQuiz() {
    Quiz quiz = quiz();
    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);
    Quiz otherQuiz = quiz();

    assertThrows(NotFoundException.class, () -> quizService.updateQuestion(otherQuiz.getId(),
        question.getId(), twoOptionQuestion(), teacherEmail));
  }

  private String studentOf(Quiz quiz) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, quiz.getModule().getCourse(), Role.STUDENT));
    return email;
  }

  private QuestionRequest twoOptionQuestion() {
    return new QuestionRequest("Which are prime?", null, List.of(
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
        new QuestionRequest(longText, null, twoOptionQuestion().options()), teacherEmail);

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
    QuestionRequest request = new QuestionRequest("Only one option", null,
        List.of(new AnswerOptionRequest("A", true)));

    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), request, teacherEmail));
  }

  @Test
  void rejectsQuestionWithNoCorrectOption() {
    Quiz quiz = quiz();
    QuestionRequest request = new QuestionRequest("No correct answer", null, List.of(
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
    quizService.publishQuiz(quiz.getId(), teacherEmail);

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

  private static QuestionRequest ordering(String... steps) {
    List<AnswerOptionRequest> options = java.util.Arrays.stream(steps)
        .map(step -> new AnswerOptionRequest(step, false)).toList();
    return new QuestionRequest("Put these in order", null, options, Question.Type.ORDERING);
  }

  @Test
  void questionsAreChoiceQuestionsUnlessTheyAskForAnotherType() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    assertEquals(Question.Type.CHOICE, question.getType());
  }

  @Test
  void storesAnOrderingQuestionWithItsOptionsInTheOrderEntered() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(), ordering("First", "Second", "Third"),
        teacherEmail);
    entityManager.flush();
    entityManager.clear();

    Question reloaded = quizService.getQuestionsWithAnswers(quiz.getId(), teacherEmail).getFirst();
    assertEquals(Question.Type.ORDERING, reloaded.getType());
    // The teacher's order is kept in the position, not in the ids.
    assertEquals(List.of("First", "Second", "Third"),
        reloaded.getOptionsInOrder().stream().map(AnswerOption::getText).toList());
    assertEquals(question.getId(), reloaded.getId());
  }

  @Test
  void anOrderingQuestionNeedsNoCorrectFlagsButStillTwoOptions() {
    Quiz quiz = quiz();

    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), ordering("Only one"), teacherEmail));
    // A choice question without a correct option is still rejected.
    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        new QuestionRequest("Q", null, List.of(new AnswerOptionRequest("A", false),
            new AnswerOptionRequest("B", false))), teacherEmail));
  }

  @Test
  void aQuestionCanBeChangedToAnotherTypeWhileTheQuizIsADraft() {
    Quiz quiz = quiz();
    Question question = quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);

    Question updated = quizService.updateQuestion(quiz.getId(), question.getId(),
        ordering("One", "Two", "Three"), teacherEmail);

    assertEquals(Question.Type.ORDERING, updated.getType());
    assertEquals(3, updated.getOptions().size());
  }

  @Test
  void teacherChangesTheAttemptLimitAndHintsAnyTime() {
    Quiz quiz = quiz();
    quizService.addQuestion(quiz.getId(), twoOptionQuestion(), teacherEmail);
    quizService.publishQuiz(quiz.getId(), teacherEmail);

    Quiz updated = quizService.updateSettings(quiz.getId(), 5, true, teacherEmail);

    assertEquals(5, updated.getMaxAttempts());
    assertTrue(updated.isShowHints());
  }

  @Test
  void updatingSettingsKeepsWhatWasNotSent() {
    Quiz quiz = quiz();
    quizService.updateSettings(quiz.getId(), 4, true, teacherEmail);

    Quiz onlyAttempts = quizService.updateSettings(quiz.getId(), 2, null, teacherEmail);
    assertEquals(2, onlyAttempts.getMaxAttempts());
    assertTrue(onlyAttempts.isShowHints());

    Quiz onlyHints = quizService.updateSettings(quiz.getId(), null, false, teacherEmail);
    assertEquals(2, onlyHints.getMaxAttempts());
    assertEquals(false, onlyHints.isShowHints());
  }

  @Test
  void rejectsInvalidSettingsAndStudentsChangingThem() {
    Quiz quiz = quiz();
    String student = studentOf(quiz);

    assertThrows(IllegalArgumentException.class,
        () -> quizService.updateSettings(quiz.getId(), -1, null, teacherEmail));
    assertThrows(ForbiddenException.class,
        () -> quizService.updateSettings(quiz.getId(), 3, true, student));
  }

  private static QuestionRequest matching(String... pairs) {
    List<AnswerOptionRequest> options = new java.util.ArrayList<>();
    for (String pair : pairs) {
      String[] sides = pair.split("=", 2);
      options.add(new AnswerOptionRequest(sides[0], false, sides.length > 1 ? sides[1] : null));
    }
    return new QuestionRequest("Match the capitals", null, options, Question.Type.MATCHING);
  }

  private static QuestionRequest fillIn(String text, String... blanks) {
    return new QuestionRequest(text, null,
        java.util.Arrays.stream(blanks).map(blank -> new AnswerOptionRequest(blank, false)).toList(),
        Question.Type.FILL_IN);
  }

  @Test
  void storesMatchingPairsWithTheirRightHandTextInTheOrderEntered() {
    Quiz quiz = quiz();

    quizService.addQuestion(quiz.getId(), matching("Norway=Oslo", "France=Paris", "Spain=Madrid"),
        teacherEmail);
    entityManager.flush();
    entityManager.clear();

    Question reloaded = quizService.getQuestionsWithAnswers(quiz.getId(), teacherEmail).getFirst();
    assertEquals(Question.Type.MATCHING, reloaded.getType());
    assertEquals(List.of("Norway", "France", "Spain"),
        reloaded.getOptionsInOrder().stream().map(AnswerOption::getText).toList());
    assertEquals(List.of("Oslo", "Paris", "Madrid"),
        reloaded.getOptionsInOrder().stream().map(AnswerOption::getMatchText).toList());
  }

  @Test
  void aMatchingQuestionNeedsTwoPairsAndEveryRightHandItem() {
    Quiz quiz = quiz();

    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), matching("Norway=Oslo"), teacherEmail));
    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), matching("Norway=Oslo", "France"), teacherEmail));
    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), matching("Norway=Oslo", "France=  "), teacherEmail));
    assertThrows(IllegalArgumentException.class,
        () -> quizService.addQuestion(quiz.getId(), matching("Norway=Oslo", "=Paris"), teacherEmail));
  }

  @Test
  void storesAFillInQuestionWithOneOptionPerBlank() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(),
        fillIn("The capital of ___ is ___.", "Norway|Norge", "Oslo"), teacherEmail);

    assertEquals(Question.Type.FILL_IN, question.getType());
    assertEquals(List.of("Norway|Norge", "Oslo"),
        question.getOptionsInOrder().stream().map(AnswerOption::getText).toList());
  }

  @Test
  void aFillInQuestionNeedsOneBlankMarkerPerBlank() {
    Quiz quiz = quiz();

    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        fillIn("The capital of ___ is ___.", "Norway"), teacherEmail));
    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        fillIn("There is no blank here.", "Norway"), teacherEmail));
    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        fillIn("Only ___ blank.", "Norway", "Oslo"), teacherEmail));
  }

  @Test
  void everyBlankNeedsAnAcceptedAnswer() {
    Quiz quiz = quiz();

    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        fillIn("A ___ B ___", "Norway", " | "), teacherEmail));
    assertThrows(IllegalArgumentException.class, () -> quizService.addQuestion(quiz.getId(),
        fillIn("A ___", "   "), teacherEmail));
  }

  /**
   * The ids of ordering and matching options are what a student sees, and ids are handed out in
   * creation order, so the service creates those options in a random order. If it did not, sorting
   * by id would give the answer.
   */
  @Test
  void theIdsOfOrderingAndMatchingOptionsDoNotRevealTheAuthoredOrder() {
    Quiz quiz = quiz();
    int idOrderMatchedAuthoredOrder = 0;
    int questions = 30;

    for (int i = 0; i < questions; i++) {
      Question ordering = quizService.addQuestion(quiz.getId(), ordering("A", "B", "C", "D", "E"),
          teacherEmail);
      Question pairs = quizService.addQuestion(quiz.getId(),
          matching("1=a", "2=b", "3=c", "4=d", "5=e"), teacherEmail);
      for (Question question : List.of(ordering, pairs)) {
        List<Long> authored = question.getOptionsInOrder().stream().map(AnswerOption::getId).toList();
        if (authored.equals(authored.stream().sorted().toList())) {
          idOrderMatchedAuthoredOrder++;
        }
      }
    }

    // Each of the 60 questions has a 1-in-120 chance of matching by luck; matching all is impossible.
    assertTrue(idOrderMatchedAuthoredOrder < 10,
        "ids followed the authored order in " + idOrderMatchedAuthoredOrder + " of " + (questions * 2));
  }

  @Test
  void aChoiceQuestionIgnoresMatchTextAndKeepsItsCorrectFlags() {
    Quiz quiz = quiz();

    Question question = quizService.addQuestion(quiz.getId(), new QuestionRequest("Pick", null,
        List.of(new AnswerOptionRequest("A", true, "ignored"), new AnswerOptionRequest("B", false))),
        teacherEmail);

    assertEquals(null, question.getOptionsInOrder().getFirst().getMatchText());
    assertTrue(question.getOptionsInOrder().getFirst().isCorrect());
    assertEquals(false, question.getOptionsInOrder().get(1).isCorrect());
  }
}
