package com._gods.canvas2.aitutor;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiTutorProperties.class, GeminiInteractionsClient.Settings.class})
public class AiTutorConfig {

  /** A bean so tests can supply a controllable clock. */
  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  HttpClient tutorHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /** One cheap virtual thread per answer: each one spends its life waiting on the model stream. */
  @Bean(destroyMethod = "close")
  ExecutorService tutorStreamExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
