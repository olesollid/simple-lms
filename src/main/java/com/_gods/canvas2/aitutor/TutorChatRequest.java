package com._gods.canvas2.aitutor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code POST /api/ai-tutor/chat}. {@code messages} is the whole conversation (the
 * backend keeps no state). The limits stop one request from carrying an enormous prompt.
 */
public record TutorChatRequest(List<Message> messages, TutorContextRequest context) {

  public static final int MAX_MESSAGES = 40;
  public static final int MAX_USER_CHARS = 2_000;
  public static final int MAX_ASSISTANT_CHARS = 8_000;
  public static final int MAX_TOTAL_CHARS = 30_000;

  public record Message(Role role, String content) {
    public enum Role {
      @JsonProperty("user") USER,
      @JsonProperty("assistant") ASSISTANT
    }
  }

  /** @throws IllegalArgumentException (mapped to 400) when the conversation is malformed or too large */
  public void validate() {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }
    if (messages.size() > MAX_MESSAGES) {
      throw new IllegalArgumentException("The conversation is too long; start a new one");
    }
    int total = 0;
    for (Message message : messages) {
      if (message == null || message.role() == null || message.content() == null
          || message.content().isBlank()) {
        throw new IllegalArgumentException("Every message needs a role and some text");
      }
      int limit = message.role() == Message.Role.USER ? MAX_USER_CHARS : MAX_ASSISTANT_CHARS;
      if (message.content().length() > limit) {
        throw new IllegalArgumentException("A message is too long");
      }
      total += message.content().length();
    }
    if (total > MAX_TOTAL_CHARS) {
      throw new IllegalArgumentException("The conversation is too long; start a new one");
    }
    if (messages.getLast().role() != Message.Role.USER) {
      throw new IllegalArgumentException("The last message must be from the student");
    }
    if (context == null) {
      throw new IllegalArgumentException("context is required");
    }
  }

  List<TutorModelClient.Turn> turns() {
    return messages.stream()
        .map(message -> new TutorModelClient.Turn(
            message.role() == Message.Role.USER ? TutorModelClient.Turn.Role.USER : TutorModelClient.Turn.Role.ASSISTANT,
            message.content()))
        .toList();
  }
}
