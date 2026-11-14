package com._gods.canvas2.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.messaging.SubmissionCreatedEvent;
import com._gods.canvas2.messaging.SubmissionPublisher;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.User;
import com._gods.canvas2.web.dto.QuizSubmissionRequest;

@Service
public class QuizSubmissionService {

  private final QuizService quizService;
  private final EnrollmentService enrollmentService;
  private final SubmissionPublisher submissionPublisher;

  public QuizSubmissionService(QuizService quizService, EnrollmentService enrollmentService,
      SubmissionPublisher submissionPublisher) {
    this.quizService = quizService;
    this.enrollmentService = enrollmentService;
    this.submissionPublisher = submissionPublisher;
  }

  /**
   * Validates the quiz exists and the requester is enrolled, then publishes the submission for
   * asynchronous grading. Grading happens out-of-band (see {@code SubmissionGradingListener}), so
   * this returns before a grade exists -- callers should poll the grade endpoint.
   */
  @Transactional(readOnly = true)
  public void submit(Long quizId, QuizSubmissionRequest request, String requesterEmail) {
    if (request.answers() == null) {
      throw new IllegalArgumentException("Answers must not be null");
    }

    Quiz quiz = quizService.getQuiz(quizId);
    enrollmentService.requireEnrolled(quiz.getModule().getCourse().getId(), requesterEmail);

    SubmissionCreatedEvent event = new SubmissionCreatedEvent(quizId,
        User.normalizeEmail(requesterEmail), request.answers(), LocalDateTime.now());
    submissionPublisher.publish(event);
  }
}
