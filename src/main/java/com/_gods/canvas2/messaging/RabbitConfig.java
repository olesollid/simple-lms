package com._gods.canvas2.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the submissions exchange/queue/binding topology. These beans are declarative only
 * (they describe what should exist on the broker, via RabbitAdmin, once a connection is actually
 * made) and don't themselves connect to RabbitMQ at startup, so they're safe to register in every
 * profile. The eager-connecting piece is the {@code @RabbitListener} consumer, which is
 * profile-gated separately in {@link SubmissionGradingListener}.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitConfig {

  public static final String SUBMISSIONS_EXCHANGE = "canvas2.submissions";
  public static final String SUBMISSIONS_QUEUE = "canvas2.submissions.quiz-grading";
  public static final String SUBMISSIONS_ROUTING_KEY = "submission.created";

  @Bean
  TopicExchange submissionsExchange() {
    return new TopicExchange(SUBMISSIONS_EXCHANGE);
  }

  @Bean
  Queue submissionsQueue() {
    return new Queue(SUBMISSIONS_QUEUE, true);
  }

  @Bean
  Binding submissionsBinding(Queue submissionsQueue, TopicExchange submissionsExchange) {
    return BindingBuilder.bind(submissionsQueue).to(submissionsExchange)
        .with(SUBMISSIONS_ROUTING_KEY);
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
      MessageConverter jsonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter);
    return template;
  }
}
