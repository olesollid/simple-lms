package com._gods.canvas2.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.web.dto.AnswerOptionRequest;
import com._gods.canvas2.web.dto.QuestionRequest;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class QuizService {

  private final ContentRepository contentRepository;
  private final EnrollmentService enrollmentService;
  private final EntityManager entityManager;

  public QuizService(ContentRepository contentRepository, EnrollmentService enrollmentService,
      EntityManager entityManager) {
    this.contentRepository = contentRepository;
    this.enrollmentService = enrollmentService;
    this.entityManager = entityManager;
  }

  /** Teacher-only: adds a question with its answer options to a quiz. */
  public Question addQuestion(Long quizId, QuestionRequest request, String requesterEmail) {
    if (request.text() == null || request.text().isBlank()) {
      throw new IllegalArgumentException("Question text must not be blank");
    }
    if (request.options() == null || request.options().size() < 2) {
      throw new IllegalArgumentException("A question needs at least two answer options");
    }
    if (request.options().stream().noneMatch(AnswerOptionRequest::correct)) {
      throw new IllegalArgumentException("A question needs at least one correct answer option");
    }

    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);

    Question question = quiz.addQuestion(request.text());
    for (AnswerOptionRequest option : request.options()) {
      if (option.text() == null || option.text().isBlank()) {
        throw new IllegalArgumentException("Answer option text must not be blank");
      }
      question.addOption(option.text(), option.correct());
    }

    entityManager.persist(question);
    for (AnswerOption option : question.getOptions()) {
      entityManager.persist(option);
    }
    entityManager.flush();
    return question;
  }

  /**
   * Any enrolled member may read the questions. Returned entities still carry
   * {@link com._gods.canvas2.model.AnswerOption#isCorrect()}; callers serving students must map
   * through a response DTO that omits it (e.g. {@code QuestionResponse}), never the
   * answers-included one.
   */
  @Transactional(readOnly = true)
  public List<Question> getQuestions(Long quizId, String requesterEmail) {
    Quiz quiz = getQuiz(quizId);
    enrollmentService.requireEnrolled(quiz.getModule().getCourse().getId(), requesterEmail);
    return initializeQuestions(quiz);
  }

  /** Teacher-only: same questions, but the caller is explicitly asserting they may see answers. */
  @Transactional(readOnly = true)
  public List<Question> getQuestionsWithAnswers(Long quizId, String requesterEmail) {
    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);
    return initializeQuestions(quiz);
  }

  /**
   * Forces both the questions collection and each question's options collection to load before
   * the transaction closes -- accessing {@code quiz.getQuestions()} alone only initializes the
   * outer collection, leaving each {@code Question.options} lazy and inaccessible to callers that
   * read the result after this method's transaction has ended.
   */
  private List<Question> initializeQuestions(Quiz quiz) {
    quiz.initializeQuestions();
    List<Question> questions = quiz.getQuestions();
    for (Question question : questions) {
      question.initializeOptions();
    }
    return questions;
  }

  @Transactional(readOnly = true)
  public Quiz getQuiz(Long quizId) {
    Content content = contentRepository.findById(quizId)
        .orElseThrow(() -> new QuizNotFoundException(quizId));
    if (!(content instanceof Quiz quiz)) {
      throw new QuizNotFoundException(quizId);
    }
    return quiz;
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
