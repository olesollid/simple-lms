package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com._gods.canvas2.aitutor.TutorModelClient.Cancellation;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real client against a local server that speaks Gemini's streaming format. */
class GeminiInteractionsClientTests {

  private static final String KEY = "secret-test-key-123";
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static String sse(String eventType, String json) {
    return "event: " + eventType + "\ndata: " + json + "\n\n";
  }

  private static String textStep(int index, String... pieces) {
    StringBuilder out = new StringBuilder(sse("step.start",
        "{\"index\":" + index + ",\"step\":{\"type\":\"model_output\"},\"event_type\":\"step.start\"}"));
    for (String piece : pieces) {
      out.append(sse("step.delta", "{\"index\":" + index + ",\"delta\":{\"text\":\"" + piece
          + "\",\"type\":\"text\"},\"event_type\":\"step.delta\"}"));
    }
    return out.append(sse("step.stop", "{\"index\":" + index + ",\"event_type\":\"step.stop\"}")).toString();
  }

  private static final String THOUGHT = sse("step.start",
      "{\"index\":0,\"step\":{\"type\":\"thought\"},\"event_type\":\"step.start\"}")
      + sse("step.delta", "{\"index\":0,\"delta\":{\"type\":\"thought_summary\",\"content\":"
      + "{\"type\":\"text\",\"text\":\"SECRET REASONING\"}},\"event_type\":\"step.delta\"}")
      + sse("step.delta", "{\"index\":0,\"delta\":{\"text\":\"SECRET REASONING 2\",\"type\":\"text\"},"
      + "\"event_type\":\"step.delta\"}")
      + sse("step.stop", "{\"index\":0,\"event_type\":\"step.stop\"}");

  private static final String COMPLETED = sse("interaction.completed",
      "{\"interaction\":{\"status\":\"completed\"},\"event_type\":\"interaction.completed\"}")
      + "event: done\ndata: [DONE]\n\n";

  private GeminiInteractionsClient client(String key, int port) {
    GeminiInteractionsClient.Settings properties = new GeminiInteractionsClient.Settings(key, "http://127.0.0.1:" + port, "test-model",
        256, Duration.ofSeconds(5));
    return new GeminiInteractionsClient(properties, mapper, HttpClient.newHttpClient());
  }

  private int serve(int status, String body, AtomicReference<JsonNode> seenBody,
      AtomicReference<com.sun.net.httpserver.Headers> seenHeaders) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/interactions", exchange -> {
      if (seenBody != null) {
        seenBody.set(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      }
      if (seenHeaders != null) {
        seenHeaders.set(exchange.getRequestHeaders());
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
    return server.getAddress().getPort();
  }

  private static TutorModelClient.Request request() {
    return new TutorModelClient.Request("Be a tutor.", List.of(
        new TutorModelClient.Turn(TutorModelClient.Turn.Role.USER, "What is a primary key?"),
        new TutorModelClient.Turn(TutorModelClient.Turn.Role.ASSISTANT, "A unique row identifier."),
        new TutorModelClient.Turn(TutorModelClient.Turn.Role.USER, "And a foreign key?")));
  }

  @Test
  void sendsTheConversationStatelesslyWithTheKeyInAHeader() throws Exception {
    AtomicReference<JsonNode> body = new AtomicReference<>();
    AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();
    int port = serve(200, textStep(0, "ok") + COMPLETED, body, headers);

    client(KEY, port).stream(request(), text -> { }, new Cancellation());

    JsonNode sent = body.get();
    assertEquals("test-model", sent.path("model").asString());
    assertEquals("Be a tutor.", sent.path("system_instruction").asString());
    assertTrue(sent.path("stream").asBoolean());
    assertFalse(sent.path("store").asBoolean(true));
    assertEquals(256, sent.path("generation_config").path("max_output_tokens").asInt());
    JsonNode input = sent.path("input");
    assertEquals(3, input.size());
    assertEquals("user_input", input.get(0).path("type").asString());
    assertEquals("model_output", input.get(1).path("type").asString());
    assertEquals("And a foreign key?", input.get(2).path("content").get(0).path("text").asString());
    assertEquals(KEY, headers.get().getFirst("x-goog-api-key"));
    assertEquals("2026-05-20", headers.get().getFirst("Api-Revision"));
    // The key travels in a header only, never in the URL or the body.
    assertFalse(mapper.writeValueAsString(sent).contains(KEY));
  }

  @Test
  void forwardsOnlyTheAnswerTextInOrderAndNeverTheReasoning() throws Exception {
    int port = serve(200, THOUGHT + textStep(1, "A column that ", "references another.") + COMPLETED,
        null, null);
    List<String> received = new ArrayList<>();

    client(KEY, port).stream(request(), received::add, new Cancellation());

    assertEquals(List.of("A column that ", "references another."), received);
  }

  @Test
  void skipsUnknownEventsAndMalformedData() throws Exception {
    String body = sse("interaction.created", "{\"event_type\":\"interaction.created\"}")
        + "data: this is not json\n\n"
        + sse("mystery.event", "{\"event_type\":\"mystery.event\",\"x\":1}")
        + textStep(0, "fine") + COMPLETED;
    int port = serve(200, body, null, null);
    List<String> received = new ArrayList<>();

    client(KEY, port).stream(request(), received::add, new Cancellation());

    assertEquals(List.of("fine"), received);
  }

  @Test
  void aStreamErrorEventBecomesAModelException() throws Exception {
    int port = serve(200, textStep(0, "Partial") + sse("error",
        "{\"error\":{\"message\":\"boom with details\",\"code\":\"gateway_timeout\"},\"event_type\":\"error\"}"),
        null, null);
    List<String> received = new ArrayList<>();

    TutorModelClient.Failure failure = assertThrows(TutorModelClient.Failure.class,
        () -> client(KEY, port).stream(request(), received::add, new Cancellation()));

    assertEquals(List.of("Partial"), received);
    assertTrue(failure.getMessage().contains("gateway_timeout"));
  }

  @Test
  void aNon200ResponseBecomesAModelExceptionWithTheStatusAndNoSecrets() throws Exception {
    int port = serve(503, "{\"error\":{\"message\":\"high demand\"}}", null, null);

    TutorModelClient.Failure failure = assertThrows(TutorModelClient.Failure.class,
        () -> client(KEY, port).stream(request(), text -> { }, new Cancellation()));

    assertEquals(503, failure.getStatus());
    assertFalse(failure.getMessage().contains(KEY));
  }

  @Test
  void reportsUnconfiguredWithoutCallingTheServer() throws Exception {
    int port = serve(200, COMPLETED, null, null);
    GeminiInteractionsClient unconfigured = client("  ", port);

    assertFalse(unconfigured.isConfigured());
    assertThrows(TutorModelClient.Failure.class,
        () -> unconfigured.stream(request(), text -> { }, new Cancellation()));
  }

  @Test
  void anUnreachableServerBecomesAModelException() {
    TutorModelClient.Failure failure = assertThrows(TutorModelClient.Failure.class,
        () -> client(KEY, 1).stream(request(), text -> { }, new Cancellation()));

    assertFalse(failure.getMessage().contains(KEY));
  }

  @Test
  void cancellingStopsAHangingStreamPromptly() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/interactions", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream out = exchange.getResponseBody();
      out.write(textStep(0, "first").getBytes(StandardCharsets.UTF_8));
      out.flush();
      try {
        release.await(15, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      exchange.close();
    });
    server.start();

    Cancellation cancellation = new Cancellation();
    CountDownLatch gotFirst = new CountDownLatch(1);
    List<String> received = new ArrayList<>();
    GeminiInteractionsClient client = client(KEY, server.getAddress().getPort());
    CompletableFuture<Void> call = CompletableFuture.runAsync(() ->
        client.stream(request(), text -> {
          received.add(text);
          gotFirst.countDown();
        }, cancellation));

    assertTrue(gotFirst.await(5, TimeUnit.SECONDS));
    cancellation.cancel();
    call.get(3, TimeUnit.SECONDS);
    release.countDown();

    assertEquals(List.of("first"), received);
  }

  @Nested
  class SettingsBinding {

    private static GeminiInteractionsClient.Settings bind(Map<String, String> values) {
      return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("app.gemini", GeminiInteractionsClient.Settings.class);
    }

    @Test
    void hasNoKeyAndSensibleDefaultsWhenNothingIsConfigured() {
      GeminiInteractionsClient.Settings properties = bind(Map.of());

      assertFalse(properties.hasApiKey());
      assertEquals("gemini-3.6-flash", properties.model());
      assertEquals("https://generativelanguage.googleapis.com/v1beta", properties.baseUrl());
      assertEquals(Duration.ofSeconds(60), properties.timeout());
    }

    @Test
    void treatsABlankKeyAsMissing() {
      assertFalse(bind(Map.of("app.gemini.api-key", "   ")).hasApiKey());
      assertTrue(bind(Map.of("app.gemini.api-key", "abc")).hasApiKey());
    }

    @Test
    void neverPrintsTheKey() {
      GeminiInteractionsClient.Settings properties = bind(Map.of("app.gemini.api-key", "super-secret-key"));

      assertFalse(properties.toString().contains("super-secret-key"));
      assertTrue(properties.toString().contains("****"));
    }
  }
}
