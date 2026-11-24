package com._gods.simplelms.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.grading.QuizGradeStore;
import com._gods.simplelms.messaging.SubmissionCreatedEvent;
import com._gods.simplelms.messaging.SubmissionPublisher;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.User;
import com._gods.simplelms.web.dto.QuizAttemptsResponse;
import com._gods.simplelms.web.dto.QuizStudentResultResponse;
import com._gods.simplelms.web.dto.QuizSubmissionRequest;

@Service
public class QuizSubmissionService {

  private final QuizService quizService;
  private final EnrollmentService enrollmentService;
  private final ModuleService moduleService;
  private final SubmissionPublisher submissionPublisher;
  private final QuizGradeStore quizGradeStore;

  public QuizSubmissionService(QuizService quizService, EnrollmentService enrollmentService,
      ModuleService moduleService, SubmissionPublisher submissionPublisher,
      QuizGradeStore quizGradeStore) {
    this.quizService = quizService;
    this.enrollmentService = enrollmentService;
    this.moduleService = moduleService;
    this.submissionPublisher = submissionPublisher;
    this.quizGradeStore = quizGradeStore;
  }

  /**
   * Checks the quiz is open to the requester, claims one of their attempts, then publishes the
   * answers for asynchronous grading (see {@code SubmissionGradingListener}). Returns the attempt
   * number; the grade for it appears in {@link #getOwnAttempts} once grading is done.
   */
  @Transactional(readOnly = true)
  public int submit(Long quizId, QuizSubmissionRequest request, String requesterEmail) {
    if (request.answers() == null) {
      throw new IllegalArgumentException("Answers must not be null");
    }
    validateTextAnswers(request.textAnswers());

    Quiz quiz = requireOpenQuiz(quizId, requesterEmail);
    if (quiz.isClosedAt(LocalDateTime.now(HandInPolicy.ZONE))) {
      throw new ForbiddenException("The deadline has passed. This quiz no longer accepts attempts.");
    }
    String studentEmail = User.normalizeEmail(requesterEmail);
    int attempt = quizGradeStore.reserveAttempt(quiz, studentEmail)
        .orElseThrow(() -> new ConflictException(
            "You have used all " + quiz.getMaxAttempts() + " attempts for this quiz"));

    try {
      submissionPublisher.publish(new SubmissionCreatedEvent(quizId, studentEmail, attempt,
          request.answers(), request.textAnswers(), LocalDateTime.now()));
    } catch (RuntimeException e) {
      quizGradeStore.releaseAttempt(quiz, studentEmail);
      throw e;
    }
    return attempt;
  }

  private static final int MAX_BLANKS_PER_QUESTION = 20;
  private static final int MAX_TEXT_ANSWER_LENGTH = 200;

  /** Typed answers come from the browser, so bound their size before they go on the queue. */
  private static void validateTextAnswers(Map<Long, List<String>> textAnswers) {
    if (textAnswers == null) {
      return;
    }
    for (List<String> typed : textAnswers.values()) {
      if (typed == null || typed.size() > MAX_BLANKS_PER_QUESTION) {
        throw new IllegalArgumentException("Too many answers for one question");
      }
      for (String answer : typed) {
        if (answer != null && answer.length() > MAX_TEXT_ANSWER_LENGTH) {
          throw new IllegalArgumentException(
              "An answer may be at most " + MAX_TEXT_ANSWER_LENGTH + " characters");
        }
      }
    }
  }

  /**
   * The requester's own attempt count and, when the results are visible to students, their best
   * and latest grade and the hints. Results are visible straight away unless the teacher turned
   * that off, and then once the teacher publishes the grades
   * ({@link Quiz#areResultsVisibleToStudents()}). Hints go with the results, because a hint gives
   * away which questions were wrong.
   */
  @Transactional(readOnly = true)
  public QuizAttemptsResponse getOwnAttempts(Long quizId, String requesterEmail) {
    Quiz quiz = requireOpenQuiz(quizId, requesterEmail);
    int attemptsUsed = quizGradeStore.attemptsUsed(quiz, requesterEmail);
    boolean closed = quiz.isClosedAt(LocalDateTime.now(HandInPolicy.ZONE));
    QuizGrade latest = quizGradeStore.findLatest(quiz, requesterEmail).orElse(null);
    // Always given, even while results are hidden, so a student can tell "still grading" from
    // "graded, but not visible yet".
    int attemptsGraded = latest == null ? 0 : latest.attemptNumber();
    if (!quiz.areResultsVisibleToStudents()) {
      return new QuizAttemptsResponse(attemptsUsed, quiz.getMaxAttempts(), quiz.getDeadline(),
          closed, false, null, null, Map.of(), attemptsGraded);
    }
    return new QuizAttemptsResponse(attemptsUsed, quiz.getMaxAttempts(), quiz.getDeadline(),
        closed, true, quizGradeStore.find(quiz, requesterEmail).orElse(null), latest,
        hintsForWrongAnswers(quiz, latest), attemptsGraded);
  }

  /** Hints for exactly the questions the latest attempt got wrong; empty before any attempt. */
  private static Map<Long, String> hintsForWrongAnswers(Quiz quiz, QuizGrade latest) {
    if (latest == null || latest.incorrectQuestionIds() == null) {
      return Map.of();
    }
    quiz.initializeQuestions();
    Map<Long, String> hints = new LinkedHashMap<>();
    for (Question question : quiz.getQuestions()) {
      if (question.getHint() != null && latest.incorrectQuestionIds().contains(question.getId())) {
        hints.put(question.getId(), question.getHint());
      }
    }
    return hints;
  }

  /**
   * Teacher-only: every student in the course with their attempt count and best grade, including
   * students who have not attempted the quiz yet, sorted by name.
   */
  @Transactional(readOnly = true)
  public List<QuizStudentResultResponse> getResults(Long quizId, String requesterEmail) {
    Quiz quiz = quizService.getQuiz(quizId);
    Long courseId = quiz.getModule().getCourse().getId();
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may see quiz results");
    }
    return enrollmentService.getEnrollmentsByCourse(courseId, Role.STUDENT, requesterEmail).stream()
        .map(Enrollment::getUser)
        .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
        .map(student -> new QuizStudentResultResponse(student.getEmail(), student.getName(),
            quizGradeStore.attemptsUsed(quiz, student.getEmail()),
            quizGradeStore.find(quiz, student.getEmail()).orElse(null)))
        .toList();
  }

  /**
   * Students only see a quiz that is published, inside its visibility window and in an unlocked
   * module -- the same rules as every other content item. A quiz that is not open looks missing.
   */
  private Quiz requireOpenQuiz(Long quizId, String requesterEmail) {
    Quiz quiz = quizService.getQuiz(quizId);
    Long courseId = quiz.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    boolean isTeacher = enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER);
    if (!isTeacher && (!quiz.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE))
        || moduleService.isLocked(quiz.getModule(), requesterEmail))) {
      throw new QuizNotFoundException(quizId);
    }
    return quiz;
  }
}
