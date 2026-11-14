package com._gods.canvas2.grading;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

  /**
   * Reuses the application's own {@link ObjectMapper} bean (auto-configured by
   * spring-boot-starter-jackson with JSR-310 support already wired in, the same one the web layer
   * uses) rather than building a second, differently-configured mapper from scratch.
   *
   * <p>Uses the type-bound {@link JacksonJsonRedisSerializer} rather than
   * {@code GenericJacksonJsonRedisSerializer}: the generic serializer deserializes without
   * knowing the target type unless the JSON carries polymorphic type metadata, which it doesn't
   * add by default -- it would hand back a raw {@code LinkedHashMap} instead of a
   * {@link QuizGrade}. Binding the serializer to {@code QuizGrade.class} avoids that entirely.
   */
  @Bean
  RedisTemplate<String, QuizGrade> quizGradeRedisTemplate(RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper) {
    RedisTemplate<String, QuizGrade> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new JacksonJsonRedisSerializer<>(objectMapper, QuizGrade.class));
    template.afterPropertiesSet();
    return template;
  }
}
