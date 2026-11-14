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

/**
 * Consumes submission events and writes a graded result to Redis. Profile-gated to "dev" (the
 * only profile with real {@code spring.rabbitmq.*} connection properties): a {@code @RabbitListener}
 * container connects to the broker eagerly at application startup, unlike the declarative
 * exchange/queue/binding beans in {@link RabbitConfig}, so registering this bean in every profile
 * would make tests and CI depend on a reachable broker.
 */
@Component
@Profile("dev")
public class SubmissionGradingListener {

  private final QuizService quizService;
  private final QuizGrader quizGrader;
  private final QuizGradeStore quizGradeStore;

  public SubmissionGradingListener(QuizService quizService, QuizGrader quizGrader,
      QuizGradeStore quizGradeStore) {
    this.quizService = quizService;
    this.quizGrader = quizGrader;
    this.quizGradeStore = quizGradeStore;
  }

  @RabbitListener(queues = RabbitConfig.SUBMISSIONS_QUEUE)
  @Transactional(readOnly = true)
  public void onSubmissionCreated(SubmissionCreatedEvent event) {
    Quiz quiz = quizService.getQuiz(event.quizId());
    QuizGrade grade = quizGrader.grade(quiz, event.studentEmail(), event.answers());
    quizGradeStore.save(grade);
  }
}
