package com._gods.canvas2.aitutor;

/** The user sent too many tutor requests. Mapped to 429 with a Retry-After by the web layer. */
public class RateLimitExceededException extends RuntimeException {
  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super("You've sent too many messages. Please wait " + retryAfterSeconds
        + " seconds and try again.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
