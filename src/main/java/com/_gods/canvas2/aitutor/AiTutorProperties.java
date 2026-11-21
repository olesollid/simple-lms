package com._gods.canvas2.aitutor;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the AI tutor. It is off by default and has one switch: {@code app.ai-tutor.enabled}
 * (the {@code AI_TUTOR_ENABLED} environment variable). When it is on, the tutor is available to the
 * students of every course.
 */
@ConfigurationProperties("app.ai-tutor")
public record AiTutorProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue RateLimit rateLimit) {

  /** At most {@code maxRequests} chat requests per user within any {@code window}. */
  public record RateLimit(@DefaultValue("20") int maxRequests, @DefaultValue("1m") Duration window) {
    public RateLimit {
      if (maxRequests < 1) {
        throw new IllegalArgumentException("app.ai-tutor.rate-limit.max-requests must be at least 1");
      }
      if (window == null || window.isZero() || window.isNegative()) {
        throw new IllegalArgumentException("app.ai-tutor.rate-limit.window must be positive");
      }
    }
  }
}
