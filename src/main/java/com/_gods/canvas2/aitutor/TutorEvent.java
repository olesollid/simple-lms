package com._gods.canvas2.aitutor;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One server-sent event to the widget; matches the contract in the frontend's types/aiTutor.ts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorEvent(String type, String text, String message, String code) {

  public static TutorEvent delta(String text) {
    return new TutorEvent("delta", text, null, null);
  }

  public static TutorEvent done() {
    return new TutorEvent("done", null, null, null);
  }

  public static TutorEvent error(String message, String code) {
    return new TutorEvent("error", null, message, code);
  }

  /** Where answer events go. The web layer writes them to an SSE response; tests collect them. */
  public interface Sink {

    /** Sends one event. Throws if the client is gone, which stops the answer. */
    void send(TutorEvent event);

    /** Ends the stream. */
    void complete();
  }
}
