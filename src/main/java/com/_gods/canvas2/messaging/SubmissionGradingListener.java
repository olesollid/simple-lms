package com._gods.canvas2.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.grading.QuizGrade;
import com._gods.canvas2.grading.QuizGradeStore;
import com._gods.canvas2.grading.QuizGrader;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.service.QuizService;
import com._gods.canvas2.service.StudentInteractionService;

/**
 * Consumes submission events and writes a graded result to Redis. Enabled in dev and prod,
 * which configure real {@code spring.rabbitmq.*} connections: a {@code @RabbitListener}
 * container connects to the broker eagerly at application startup, unlike the declarative
 * exchange/queue/binding beans in {@link RabbitConfig}, so registering this bean in every profile
 * would make tests and CI depend on a reachable broker.
 */
@Component
@Profile({"dev", "prod"})
public class SubmissionGradingListener {

  private final QuizService quizService;
  private final QuizGrader quizGrader;
  private final QuizGradeStore quizGradeStore;
  private final StudentInteractionService studentInteractionService;

  public SubmissionGradingListener(QuizService quizService, QuizGrader quizGrader,
      QuizGradeStore quizGradeStore, StudentInteractionService studentInteractionService) {
    this.quizService = quizService;
    this.quizGrader = quizGrader;
    this.quizGradeStore = quizGradeStore;
    this.studentInteractionService = studentInteractionService;
  }

  @RabbitListener(queues = RabbitConfig.SUBMISSIONS_QUEUE)
  @Transactional
  public void onSubmissionCreated(SubmissionCreatedEvent event) {
    Quiz quiz = quizService.getQuiz(event.quizId());
    QuizGrade grade = quizGrader.grade(quiz, event.studentEmail(), event.attemptNumber(),
        event.answers());
    // Submitting a quiz is the unambiguous "done" signal for it, so mark completion
    // automatically rather than requiring a separate explicit call from the student. Done before
    // the Redis write (which the DB transaction can't roll back) so a failure here never leaves a
    // grade recorded in Redis for a quiz that was never actually marked completed; redelivery
    // after a failure safely repeats both steps, since grading is deterministic and marking an
    // already-completed interaction completed again is a no-op.
    studentInteractionService.markCompleted(quiz.getId(), event.studentEmail());
    quizGradeStore.save(quiz, grade);
  }
}
