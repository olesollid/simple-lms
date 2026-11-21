package com._gods.canvas2.aitutor;

import com._gods.canvas2.aitutor.TutorModelClient.Cancellation;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs one tutor request: checks it, builds the prompt, and streams the model's answer to a sink.
 * Everything that can refuse the request happens before streaming starts, so refusals are normal
 * HTTP errors (400, 403, 404, 429, 503). Only failures inside the model call become {@code error}
 * events on the stream.
 */
@Service
public class TutorChatService {

  /** The tutor cannot take this request right now (not configured, or too many answers in progress). */
  public static class Unavailable extends RuntimeException {
    public Unavailable(String message) {
      super(message);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(TutorChatService.class);
  private static final int MAX_CONCURRENT_ANSWERS = 50;
  private static final String PROVIDER_FAILURE = "The AI tutor is unavailable right now. Try again in a moment.";

  private final TutorAccessService accessService;
  private final TutorRateLimiter rateLimiter;
  private final TutorGroundingService groundingService;
  private final TutorPromptBuilder promptBuilder;
  private final TutorModelClient modelClient;
  private final Executor executor;
  private final Semaphore permits = new Semaphore(MAX_CONCURRENT_ANSWERS);

  public TutorChatService(TutorAccessService accessService, TutorRateLimiter rateLimiter,
      TutorGroundingService groundingService, TutorPromptBuilder promptBuilder,
      TutorModelClient modelClient, @Qualifier("tutorStreamExecutor") Executor executor) {
    this.accessService = accessService;
    this.rateLimiter = rateLimiter;
    this.groundingService = groundingService;
    this.promptBuilder = promptBuilder;
    this.modelClient = modelClient;
    this.executor = executor;
  }

  /**
   * Validates and authorizes the request, then streams the answer to {@code sink} on another thread.
   * The email is always the logged-in user's, taken from the session and never from the request.
   *
   * @return a handle the caller can use to stop the answer if the client disconnects
   */
  public Cancellation start(String email, TutorChatRequest request, TutorEvent.Sink sink) {
    request.validate();
    if (!modelClient.isConfigured()) {
      throw new Unavailable("The AI tutor is not available right now.");
    }
    // Rate limiting comes before the permission checks so probing other courses' ids is limited too.
    rateLimiter.acquire(email);
    VerifiedTutorContext context = accessService.verify(email, request.context());
    TutorModelClient.Request modelRequest = new TutorModelClient.Request(
        promptBuilder.build(context, groundingService.gather(email, context), false),
        request.turns());

    if (!permits.tryAcquire()) {
      throw new Unavailable("The AI tutor is busy. Try again in a moment.");
    }
    Cancellation cancellation = new Cancellation();
    try {
      executor.execute(() -> run(modelRequest, sink, cancellation));
    } catch (RuntimeException e) {
      permits.release();
      throw new Unavailable("The AI tutor is busy. Try again in a moment.");
    }
    return cancellation;
  }

  private void run(TutorModelClient.Request modelRequest, TutorEvent.Sink sink, Cancellation cancellation) {
    AtomicBoolean produced = new AtomicBoolean();
    try {
      modelClient.stream(modelRequest, text -> {
        produced.set(true);
        sink.send(TutorEvent.delta(text));
      }, cancellation);
      if (cancellation.isCancelled()) {
        return;
      }
      sink.send(produced.get()
          ? TutorEvent.done()
          : TutorEvent.error("The AI tutor returned an empty answer.", "empty_answer"));
    } catch (TutorModelClient.Failure e) {
      if (!cancellation.isCancelled()) {
        log.warn("Model call failed: {} (status {})", e.getMessage(), e.getStatus());
        sendQuietly(sink, TutorEvent.error(PROVIDER_FAILURE, "provider_unavailable"));
      }
    } catch (RuntimeException e) {
      // Usually the client disconnecting mid-answer; stop the model call either way.
      cancellation.cancel();
      log.debug("Tutor answer stopped: {}", e.toString());
    } finally {
      permits.release();
      try {
        sink.complete();
      } catch (RuntimeException e) {
        log.debug("Could not complete the stream: {}", e.toString());
      }
    }
  }

  private static void sendQuietly(TutorEvent.Sink sink, TutorEvent event) {
    try {
      sink.send(event);
    } catch (RuntimeException e) {
      log.debug("Could not send {} event: {}", event.type(), e.toString());
    }
  }
}
