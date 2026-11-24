package com._gods.simplelms.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.grading.QuizGrader;
import com._gods.simplelms.model.AnswerOption;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.repository.ContentRepository;
import com._gods.simplelms.repository.CourseRepository;
import com._gods.simplelms.repository.ModuleRepository;

/**
 * QuizGrader tests that need persisted entities, since the grader compares real
 * {@link AnswerOption#getId()} values, which are only assigned on save.
 */
@DataJpaTest
@Import(QuizGrader.class)
class QuizGraderDataTests {

  @Autowired
  private QuizGrader grader;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private ContentRepository contentRepository;

  private Quiz quizWithOneMultiSelectQuestion;
  private Long optionA;
  private Long optionB;
  private Long optionC;

  private void setUp() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Week 1 Quiz", 0, 1);
    Question question = quiz.addQuestion("Which are prime?");
    AnswerOption a = question.addOption("2", true);
    AnswerOption b = question.addOption("3", true);
    AnswerOption c = question.addOption("4", false);
    quizWithOneMultiSelectQuestion = (Quiz) contentRepository.save(quiz);
    optionA = a.getId();
    optionB = b.getId();
    optionC = c.getId();
  }

  @Test
  void gradesExactMultiSelectMatchAsCorrect() {
    setUp();

    QuizGrade grade = grader.grade(quizWithOneMultiSelectQuestion, "student@example.com",
        Map.of(questionId(), List.of(optionA, optionB)));

    assertEquals(1, grade.correctQuestions());
    assertEquals(1.0, grade.score());
  }

  @Test
  void gradesPartialMultiSelectMatchAsIncorrect() {
    setUp();

    QuizGrade grade = grader.grade(quizWithOneMultiSelectQuestion, "student@example.com",
        Map.of(questionId(), List.of(optionA)));

    assertEquals(0, grade.correctQuestions());
  }

  @Test
  void gradesOverSelectionIncludingWrongOptionAsIncorrect() {
    setUp();

    QuizGrade grade = grader.grade(quizWithOneMultiSelectQuestion, "student@example.com",
        Map.of(questionId(), List.of(optionA, optionB, optionC)));

    assertEquals(0, grade.correctQuestions());
  }

  @Test
  void treatsUnansweredQuestionAsIncorrect() {
    setUp();

    QuizGrade grade = grader.grade(quizWithOneMultiSelectQuestion, "student@example.com", Map.of());

    assertEquals(1, grade.totalQuestions());
    assertEquals(0, grade.correctQuestions());
  }

  @Test
  void computesScoreAcrossMultipleQuestions() {
    Course course = courseRepository.save(new Course("Intro", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Quiz", 0, 1);
    Question first = quiz.addQuestion("First");
    AnswerOption firstCorrect = first.addOption("Right", true);
    first.addOption("Wrong", false);
    Question second = quiz.addQuestion("Second");
    AnswerOption secondCorrect = second.addOption("Right", true);
    second.addOption("Wrong", false);
    Quiz saved = (Quiz) contentRepository.save(quiz);

    QuizGrade grade = grader.grade(saved, "student@example.com", Map.of(
        first.getId(), List.of(firstCorrect.getId()),
        second.getId(), List.of(secondCorrect.getId())));

    assertEquals(2, grade.totalQuestions());
    assertEquals(2, grade.correctQuestions());
    assertEquals(1.0, grade.score());
  }

  @Test
  void recordsAttemptNumberAndWhichQuestionsWereWrong() {
    Course course = courseRepository.save(new Course("Intro", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Quiz", 0, 0);
    Question first = quiz.addQuestion("First");
    AnswerOption firstCorrect = first.addOption("Right", true);
    first.addOption("Wrong", false);
    Question second = quiz.addQuestion("Second");
    second.addOption("Right", true);
    AnswerOption secondWrong = second.addOption("Wrong", false);
    Quiz saved = (Quiz) contentRepository.save(quiz);

    QuizGrade grade = grader.grade(saved, "student@example.com", 3, Map.of(
        first.getId(), List.of(firstCorrect.getId()),
        second.getId(), List.of(secondWrong.getId())));

    assertEquals(3, grade.attemptNumber());
    assertEquals(1, grade.correctQuestions());
    assertEquals(List.of(second.getId()), grade.incorrectQuestionIds());
  }

  private Long questionId() {
    return quizWithOneMultiSelectQuestion.getQuestions().get(0).getId();
  }

  /** An ordering question whose authored order is its options' ids in ascending order. */
  private Quiz quizWithOrderingQuestion() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Order quiz", 0, 1);
    Question question = quiz.addQuestion("Put the steps in order");
    question.setType(Question.Type.ORDERING);
    question.addOption("Plan", true);
    question.addOption("Build", true);
    question.addOption("Test", true);
    return (Quiz) contentRepository.save(quiz);
  }

  private static List<Long> authoredOrder(Quiz quiz) {
    return quiz.getQuestions().getFirst().getOptionsInOrder().stream().map(AnswerOption::getId).toList();
  }

  @Test
  void gradesAnOrderingQuestionCorrectOnlyForTheExactAuthoredOrder() {
    Quiz quiz = quizWithOrderingQuestion();
    Long questionId = quiz.getQuestions().getFirst().getId();

    QuizGrade right = grader.grade(quiz, "student@example.com", Map.of(questionId, authoredOrder(quiz)));
    QuizGrade reversed = grader.grade(quiz, "student@example.com",
        Map.of(questionId, authoredOrder(quiz).reversed()));

    assertEquals(1, right.correctQuestions());
    assertEquals(0, reversed.correctQuestions());
    assertEquals(List.of(questionId), reversed.incorrectQuestionIds());
  }

  @Test
  void anOrderingAnswerMustContainEveryOptionOnce() {
    Quiz quiz = quizWithOrderingQuestion();
    Long questionId = quiz.getQuestions().getFirst().getId();
    List<Long> order = authoredOrder(quiz);

    QuizGrade missingOne = grader.grade(quiz, "s@example.com", Map.of(questionId, order.subList(0, 2)));
    QuizGrade duplicated = grader.grade(quiz, "s@example.com",
        Map.of(questionId, List.of(order.get(0), order.get(0), order.get(1), order.get(2))));
    QuizGrade unanswered = grader.grade(quiz, "s@example.com", Map.of());

    assertEquals(0, missingOne.correctQuestions());
    assertEquals(0, duplicated.correctQuestions());
    assertEquals(0, unanswered.correctQuestions());
  }

  private Quiz quizWithMatchingQuestion(String[][] pairs) {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Match quiz", 0, 1);
    Question question = quiz.addQuestion("Match them");
    question.setType(Question.Type.MATCHING);
    // Created back to front, so the ids run opposite to the positions.
    for (int i = pairs.length - 1; i >= 0; i--) {
      question.addOption(pairs[i][0], true, pairs[i][1], i);
    }
    return (Quiz) contentRepository.save(quiz);
  }

  private static Question onlyQuestion(Quiz quiz) {
    return quiz.getQuestions().getFirst();
  }

  private static List<Long> optionIdsByPosition(Quiz quiz) {
    return onlyQuestion(quiz).getOptionsInOrder().stream().map(AnswerOption::getId).toList();
  }

  @Test
  void gradesAMatchingQuestionCorrectOnlyWhenEveryPairIsRight() {
    Quiz quiz = quizWithMatchingQuestion(new String[][] {
        {"Norway", "Oslo"}, {"France", "Paris"}, {"Spain", "Madrid"}});
    Long id = onlyQuestion(quiz).getId();
    List<Long> byPosition = optionIdsByPosition(quiz);

    QuizGrade right = grader.grade(quiz, "s@example.com", Map.of(id, byPosition));
    QuizGrade swapped = grader.grade(quiz, "s@example.com",
        Map.of(id, List.of(byPosition.get(1), byPosition.get(0), byPosition.get(2))));

    assertEquals(1, right.correctQuestions());
    assertEquals(0, swapped.correctQuestions());
    assertEquals(List.of(id), swapped.incorrectQuestionIds());
  }

  @Test
  void aMatchingAnswerMustChooseOneRightHandItemPerLeftHandItem() {
    Quiz quiz = quizWithMatchingQuestion(new String[][] {
        {"Norway", "Oslo"}, {"France", "Paris"}, {"Spain", "Madrid"}});
    Long id = onlyQuestion(quiz).getId();
    List<Long> byPosition = optionIdsByPosition(quiz);

    assertEquals(0, grader.grade(quiz, "s@example.com", Map.of(id, byPosition.subList(0, 2))).correctQuestions());
    assertEquals(0, grader.grade(quiz, "s@example.com", Map.of(id, List.of(
        byPosition.get(0), byPosition.get(1), byPosition.get(2), byPosition.get(2)))).correctQuestions());
    assertEquals(0, grader.grade(quiz, "s@example.com", Map.of(id, List.of(
        byPosition.get(0), byPosition.get(1), 999_999L))).correctQuestions());
    assertEquals(0, grader.grade(quiz, "s@example.com", Map.of()).correctQuestions());
  }

  @Test
  void pairsWithTheSameRightHandTextAreInterchangeable() {
    Quiz quiz = quizWithMatchingQuestion(new String[][] {
        {"Cat", "Mammal"}, {"Dog", "Mammal"}, {"Trout", "Fish"}});
    Long id = onlyQuestion(quiz).getId();
    List<Long> byPosition = optionIdsByPosition(quiz);

    // Cat -> the Dog pair's "Mammal" is just as right as its own.
    QuizGrade grade = grader.grade(quiz, "s@example.com",
        Map.of(id, List.of(byPosition.get(1), byPosition.get(0), byPosition.get(2))));

    assertEquals(1, grade.correctQuestions());
  }

  private Quiz quizWithFillInQuestion(String text, String... blanks) {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Fill-in quiz", 0, 1);
    Question question = quiz.addQuestion(text);
    question.setType(Question.Type.FILL_IN);
    for (int i = 0; i < blanks.length; i++) {
      question.addOption(blanks[i], true, null, i);
    }
    return (Quiz) contentRepository.save(quiz);
  }

  private QuizGrade gradeText(Quiz quiz, List<String> typed) {
    return grader.grade(quiz, "s@example.com", 1, Map.of(), Map.of(onlyQuestion(quiz).getId(), typed));
  }

  @Test
  void gradesAFillInQuestionIgnoringCaseAndSurroundingSpaces() {
    Quiz quiz = quizWithFillInQuestion("The capital of ___ is ___.", "Norway", "Oslo");

    assertEquals(1, gradeText(quiz, List.of("norway", "  OSLO ")).correctQuestions());
    assertEquals(1, gradeText(quiz, List.of("Norway", "Oslo")).correctQuestions());
  }

  @Test
  void aFillInBlankAcceptsAnyOfItsAlternatives() {
    Quiz quiz = quizWithFillInQuestion("Capital of ___ is ___.", "Norway|Norge", "Oslo|Kristiania");

    assertEquals(1, gradeText(quiz, List.of("Norge", "Kristiania")).correctQuestions());
    assertEquals(1, gradeText(quiz, List.of("Norway", "Oslo")).correctQuestions());
  }

  @Test
  void aFillInQuestionIsWrongIfAnyBlankIsWrongOrMissing() {
    Quiz quiz = quizWithFillInQuestion("The capital of ___ is ___.", "Norway", "Oslo");

    assertEquals(0, gradeText(quiz, List.of("Sweden", "Oslo")).correctQuestions());
    assertEquals(0, gradeText(quiz, List.of("Norway")).correctQuestions());
    assertEquals(0, gradeText(quiz, List.of("Norway", "Oslo", "extra")).correctQuestions());
    assertEquals(0, gradeText(quiz, java.util.Arrays.asList("Norway", null)).correctQuestions());
    assertEquals(0, grader.grade(quiz, "s@example.com", 1, Map.of(), null).correctQuestions());
    assertEquals(0, grader.grade(quiz, "s@example.com", 1, Map.of(), Map.of()).correctQuestions());
  }

  @Test
  void anEmptyAnswerNeverMatchesAnEmptyAlternative() {
    Quiz quiz = quizWithFillInQuestion("A ___", "Oslo|");

    assertEquals(0, gradeText(quiz, List.of("")).correctQuestions());
    assertEquals(1, gradeText(quiz, List.of("oslo")).correctQuestions());
  }

  @Test
  void aQuizCanMixAllFourQuestionTypes() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Quiz quiz = new Quiz(module, "Mixed", 0, 1);
    Question choice = quiz.addQuestion("Pick");
    choice.addOption("Right", true);
    choice.addOption("Wrong", false);
    Question ordering = quiz.addQuestion("Order");
    ordering.setType(Question.Type.ORDERING);
    ordering.addOption("First", true);
    ordering.addOption("Second", true);
    Question pairs = quiz.addQuestion("Match");
    pairs.setType(Question.Type.MATCHING);
    pairs.addOption("a", true, "1", 0);
    pairs.addOption("b", true, "2", 1);
    Question blank = quiz.addQuestion("Type ___");
    blank.setType(Question.Type.FILL_IN);
    blank.addOption("answer", true, null, 0);
    Quiz saved = (Quiz) contentRepository.save(quiz);
    Question c = saved.getQuestions().get(0);
    Question o = saved.getQuestions().get(1);
    Question m = saved.getQuestions().get(2);
    Question f = saved.getQuestions().get(3);

    QuizGrade grade = grader.grade(saved, "s@example.com", 1,
        Map.of(
            c.getId(), List.of(c.getOptionsInOrder().getFirst().getId()),
            o.getId(), o.getOptionsInOrder().stream().map(AnswerOption::getId).toList(),
            m.getId(), m.getOptionsInOrder().stream().map(AnswerOption::getId).toList()),
        Map.of(f.getId(), List.of("Answer")));

    assertEquals(4, grade.correctQuestions());
    assertEquals(1.0, grade.score());
  }
}
