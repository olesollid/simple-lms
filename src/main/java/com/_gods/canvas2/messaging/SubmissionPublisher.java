package com._gods.canvas2.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubmissionPublisher {

  private final RabbitTemplate rabbitTemplate;

  public SubmissionPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publish(SubmissionCreatedEvent event) {
    rabbitTemplate.convertAndSend(RabbitConfig.SUBMISSIONS_EXCHANGE,
        RabbitConfig.SUBMISSIONS_ROUTING_KEY, event);
  }
}
