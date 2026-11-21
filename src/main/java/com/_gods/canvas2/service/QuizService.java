package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.HandInPolicy;
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
  private final ModuleService moduleService;
  private final EntityManager entityManager;

  public QuizService(ContentRepository contentRepository, EnrollmentService enrollmentService,
      ModuleService moduleService, EntityManager entityManager) {
    this.contentRepository = contentRepository;
    this.enrollmentService = enrollmentService;
    this.moduleService = moduleService;
    this.entityManager = entityManager;
  }

  /** Teacher-only, draft quizzes only: adds a question with its answer options. */
  public Question addQuestion(Long quizId, QuestionRequest request, String requesterEmail) {
    validate(request);
    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);
    requireDraft(quiz);

    Question question = quiz.addQuestion(request.text(), request.hint());
    addOptions(question, request);
    entityManager.persist(question);
    for (AnswerOption option : question.getOptions()) {
      entityManager.persist(option);
    }
    entityManager.flush();
    return question;
  }

  /** Teacher-only, draft quizzes only: replaces a question's text, hint and answer options. */
  public Question updateQuestion(Long quizId, Long questionId, QuestionRequest request,
      String requesterEmail) {
    validate(request);
    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);
    requireDraft(quiz);

    Question question = findQuestion(quiz, questionId);
    question.update(request.text(), request.hint());
    question.clearOptions();
    // Flush the orphan removals first, so the old options are gone before the new ones are added.
    entityManager.flush();
    addOptions(question, request);
    for (AnswerOption option : question.getOptions()) {
      entityManager.persist(option);
    }
    entityManager.flush();
    return question;
  }

  /** Teacher-only, draft quizzes only: removes a question and its options. */
  public void deleteQuestion(Long quizId, Long questionId, String requesterEmail) {
    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);
    requireDraft(quiz);
    quiz.initializeQuestions();
    quiz.removeQuestion(findQuestion(quiz, questionId));
    entityManager.flush();
  }

  /** Teacher-only: makes the quiz available to students and freezes its questions. */
  public Quiz publishQuiz(Long quizId, String requesterEmail) {
    Quiz quiz = getQuiz(quizId);
    requireTeacher(quiz.getModule().getCourse().getId(), requesterEmail);
    requireDraft(quiz);
    quiz.initializeQuestions();
    if (quiz.getQuestions().isEmpty()) {
      throw new ConflictException("Add at least one question before publishing the quiz");
    }
    quiz.publish();
    return quiz;
  }

  /**
   * Any enrolled member may read the questions of a quiz that is open to them. Returned entities
   * still carry {@link AnswerOption#isCorrect()}; callers serving students must map through a
   * response DTO that omits it (e.g. {@code QuestionResponse}), never the answers-included one.
   */
  @Transactional(readOnly = true)
  public List<Question> getQuestions(Long quizId, String requesterEmail) {
    Quiz quiz = getQuiz(quizId);
    Long courseId = quiz.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)
        && (!quiz.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE))
            || moduleService.isLocked(quiz.getModule(), requesterEmail))) {
      throw new QuizNotFoundException(quizId);
    }
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

  private static void validate(QuestionRequest request) {
    if (request.text() == null || request.text().isBlank()) {
      throw new IllegalArgumentException("Question text must not be blank");
    }
    if (request.options() == null || request.options().size() < 2) {
      throw new IllegalArgumentException("A question needs at least two answer options");
    }
    if (request.options().stream().noneMatch(AnswerOptionRequest::correct)) {
      throw new IllegalArgumentException("A question needs at least one correct answer option");
    }
    for (AnswerOptionRequest option : request.options()) {
      if (option.text() == null || option.text().isBlank()) {
        throw new IllegalArgumentException("Answer option text must not be blank");
      }
    }
  }

  private static void addOptions(Question question, QuestionRequest request) {
    for (AnswerOptionRequest option : request.options()) {
      question.addOption(option.text(), option.correct());
    }
  }

  private Question findQuestion(Quiz quiz, Long questionId) {
    Question question = entityManager.find(Question.class, questionId);
    if (question == null || !question.getQuiz().getId().equals(quiz.getId())) {
      throw new NotFoundException("Question not found: " + questionId);
    }
    return question;
  }

  private static void requireDraft(Quiz quiz) {
    if (quiz.isPublished()) {
      throw new ConflictException("This quiz is published, so its questions can no longer change");
    }
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
