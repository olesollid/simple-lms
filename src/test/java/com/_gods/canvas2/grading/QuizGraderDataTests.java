package com._gods.canvas2.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.ModuleRepository;

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
    Quiz quiz = new Quiz(module, "Week 1 Quiz", 0, 1, false);
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
    Quiz quiz = new Quiz(module, "Quiz", 0, 1, false);
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

  private Long questionId() {
    return quizWithOneMultiSelectQuestion.getQuestions().get(0).getId();
  }
}
