package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TutorRateLimiterTests {

  /** A clock the test can move forward. */
  static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-21T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override public Instant instant() {
      return now;
    }
  }

  private static TutorRateLimiter limiter(MutableClock clock, int max, Duration window) {
    return new TutorRateLimiter(
        new AiTutorProperties(true, Set.of(), new AiTutorProperties.RateLimit(max, window)), clock);
  }

  @Test
  void allowsRequestsUpToTheLimit() {
    TutorRateLimiter limiter = limiter(new MutableClock(), 3, Duration.ofMinutes(1));

    assertDoesNotThrow(() -> {
      limiter.acquire("a@example.com");
      limiter.acquire("a@example.com");
      limiter.acquire("a@example.com");
    });
  }

  @Test
  void rejectsTheRequestOverTheLimitAndSaysHowLongToWait() {
    MutableClock clock = new MutableClock();
    TutorRateLimiter limiter = limiter(clock, 2, Duration.ofSeconds(60));
    limiter.acquire("a@example.com");
    clock.advance(Duration.ofSeconds(10));
    limiter.acquire("a@example.com");
    clock.advance(Duration.ofSeconds(5));

    RateLimitExceededException exception =
        assertThrows(RateLimitExceededException.class, () -> limiter.acquire("a@example.com"));

    // The oldest request (15 s ago) leaves the window in 45 s.
    assertEquals(45, exception.getRetryAfterSeconds());
  }

  @Test
  void aRejectedRequestDoesNotUseUpAllowance() {
    MutableClock clock = new MutableClock();
    TutorRateLimiter limiter = limiter(clock, 1, Duration.ofSeconds(30));
    limiter.acquire("a@example.com");
    assertThrows(RateLimitExceededException.class, () -> limiter.acquire("a@example.com"));
    assertThrows(RateLimitExceededException.class, () -> limiter.acquire("a@example.com"));

    clock.advance(Duration.ofSeconds(30));

    assertDoesNotThrow(() -> limiter.acquire("a@example.com"));
  }

  @Test
  void theWindowSlides() {
    MutableClock clock = new MutableClock();
    TutorRateLimiter limiter = limiter(clock, 2, Duration.ofSeconds(60));
    limiter.acquire("a@example.com");
    clock.advance(Duration.ofSeconds(40));
    limiter.acquire("a@example.com");
    clock.advance(Duration.ofSeconds(25));

    // The first request is now 65 s old, so exactly one slot is free again.
    assertDoesNotThrow(() -> limiter.acquire("a@example.com"));
    assertThrows(RateLimitExceededException.class, () -> limiter.acquire("a@example.com"));
  }

  @Test
  void usersAreLimitedIndependentlyAndEmailsAreCaseInsensitive() {
    TutorRateLimiter limiter = limiter(new MutableClock(), 1, Duration.ofMinutes(1));
    limiter.acquire("a@example.com");

    assertDoesNotThrow(() -> limiter.acquire("b@example.com"));
    assertThrows(RateLimitExceededException.class, () -> limiter.acquire("A@Example.com"));
  }

  @Test
  void reportsAtLeastOneSecond() {
    MutableClock clock = new MutableClock();
    TutorRateLimiter limiter = limiter(clock, 1, Duration.ofSeconds(60));
    limiter.acquire("a@example.com");
    clock.advance(Duration.ofMillis(59_900));

    RateLimitExceededException exception =
        assertThrows(RateLimitExceededException.class, () -> limiter.acquire("a@example.com"));

    assertEquals(1, exception.getRetryAfterSeconds());
  }
}
