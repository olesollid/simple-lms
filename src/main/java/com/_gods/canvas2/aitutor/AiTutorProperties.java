package com._gods.canvas2.aitutor;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Feature flags and limits for the AI tutor. Everything is off by default: the tutor is available
 * only when {@code app.ai-tutor.enabled} is true (global switch) and the course is listed in
 * {@code app.ai-tutor.enabled-course-ids} (per-course opt-in).
 */
@ConfigurationProperties("app.ai-tutor")
public record AiTutorProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue Set<Long> enabledCourseIds,
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

  public boolean isCourseEnabled(Long courseId) {
    return enabled && courseId != null && enabledCourseIds.contains(courseId);
  }
}
