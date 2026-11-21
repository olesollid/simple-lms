package com._gods.canvas2.aitutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com._gods.canvas2.model.User;

/**
 * Per-user sliding-window rate limit for tutor requests. State is in memory, so it is per
 * application instance; that is enough for a single-container deployment and keeps the limit
 * working without Redis. Each call that passes counts as one request.
 */
@Component
public class TutorRateLimiter {

  private static final long SWEEP_EVERY = 256;

  private final int maxRequests;
  private final Duration window;
  private final Clock clock;
  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final AtomicLong calls = new AtomicLong();

  public TutorRateLimiter(AiTutorProperties properties, Clock clock) {
    this.maxRequests = properties.rateLimit().maxRequests();
    this.window = properties.rateLimit().window();
    this.clock = clock;
  }

  /** Records a request for this user, or throws if they have used up their allowance. */
  public void acquire(String email) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    Deque<Instant> userHits = hits.computeIfAbsent(User.normalizeEmail(email), key -> new ArrayDeque<>());
    synchronized (userHits) {
      evictExpired(userHits, cutoff);
      if (userHits.size() >= maxRequests) {
        Duration wait = Duration.between(now, userHits.peekFirst().plus(window));
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        throw new RateLimitExceededException(seconds);
      }
      userHits.addLast(now);
    }
    if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
      sweep(cutoff);
    }
  }

  private static void evictExpired(Deque<Instant> userHits, Instant cutoff) {
    while (!userHits.isEmpty() && !userHits.peekFirst().isAfter(cutoff)) {
      userHits.pollFirst();
    }
  }

  /** Drops users who have been idle for a whole window so the map cannot grow without bound. */
  private void sweep(Instant cutoff) {
    hits.forEach((email, userHits) -> {
      synchronized (userHits) {
        evictExpired(userHits, cutoff);
        if (userHits.isEmpty()) {
          hits.remove(email, userHits);
        }
      }
    });
  }
}
