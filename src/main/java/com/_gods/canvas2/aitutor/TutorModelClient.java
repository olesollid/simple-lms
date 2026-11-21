package com._gods.canvas2.aitutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** The model provider behind the tutor. Kept as an interface so tests never call a real model. */
public interface TutorModelClient {

  /** False when no API key is configured, in which case the tutor is reported as off. */
  boolean isConfigured();

  /**
   * Streams the answer, calling {@code onText} for each piece of answer text (never reasoning
   * text). Blocks until the answer ends, fails, or {@code cancellation} is triggered.
   *
   * @throws Failure if the provider fails or cannot be reached
   */
  void stream(Request request, Consumer<String> onText, Cancellation cancellation);

  /** What the model is asked: the instructions, then the conversation (the last turn is the student's). */
  record Request(String systemInstruction, List<Turn> turns) {
  }

  /** One message of the conversation, as sent to the model. */
  record Turn(Role role, String text) {
    public enum Role { USER, ASSISTANT }
  }

  /** The model provider failed or could not be reached. The message is safe to log, never shown to students. */
  class Failure extends RuntimeException {
    private final int status;

    public Failure(String message, int status, Throwable cause) {
      super(message, cause);
      this.status = status;
    }

    public Failure(String message) {
      this(message, 0, null);
    }

    /** The provider's HTTP status, or 0 when there was none (network failure, stream error). */
    public int getStatus() {
      return status;
    }
  }

  /** Lets the web layer stop an in-flight model call, for example when the browser disconnects. */
  final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> hooks = new CopyOnWriteArrayList<>();

    public boolean isCancelled() {
      return cancelled.get();
    }

    public void cancel() {
      if (cancelled.compareAndSet(false, true)) {
        hooks.forEach(Runnable::run);
      }
    }

    /** Runs the hook on cancel, or immediately if already cancelled. Used to close a blocked stream. */
    public void onCancel(Runnable hook) {
      hooks.add(hook);
      if (cancelled.get()) {
        hook.run();
      }
    }
  }
}
