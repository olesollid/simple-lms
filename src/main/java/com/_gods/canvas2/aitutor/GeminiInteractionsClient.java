package com._gods.canvas2.aitutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Talks to the Gemini Interactions API over REST, statelessly: each request carries the whole
 * conversation and {@code store} is false, so Google keeps nothing. Only {@code model_output}
 * text is forwarded; thought steps are dropped. Student messages and the API key are never logged.
 */
@Component
public class GeminiInteractionsClient implements TutorModelClient {

  /**
   * Settings for the Gemini API. The key is a server-side secret: it comes from the environment,
   * is only ever sent to Google in a request header, and is redacted from {@link #toString()}.
   */
  @ConfigurationProperties("app.gemini")
  public record Settings(
      @DefaultValue("") String apiKey,
      @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
      @DefaultValue("gemini-3.5-flash-lite") String model,
      @DefaultValue("1024") int maxOutputTokens,
      @DefaultValue("60s") Duration timeout) {

    public boolean hasApiKey() {
      return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
      return "GeminiInteractionsClient.Settings[apiKey=" + (hasApiKey() ? "****" : "<unset>") + ", baseUrl=" + baseUrl
          + ", model=" + model + ", maxOutputTokens=" + maxOutputTokens + ", timeout=" + timeout + "]";
    }
  }

  private static final Logger log = LoggerFactory.getLogger(GeminiInteractionsClient.class);
  private static final String API_REVISION = "2026-05-20";

  private final Settings properties;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  public GeminiInteractionsClient(Settings properties, ObjectMapper mapper,
      @Qualifier("tutorHttpClient") HttpClient httpClient) {
    this.properties = properties;
    this.mapper = mapper;
    this.httpClient = httpClient;
  }

  @Override
  public boolean isConfigured() {
    return properties.hasApiKey();
  }

  @Override
  public void stream(Request request, Consumer<String> onText, Cancellation cancellation) {
    if (!isConfigured()) {
      throw new Failure("No Gemini API key is configured");
    }
    HttpRequest httpRequest = HttpRequest.newBuilder(
            URI.create(properties.baseUrl().replaceAll("/+$", "") + "/interactions"))
        .timeout(properties.timeout())
        .header("x-goog-api-key", properties.apiKey())
        .header("Api-Revision", API_REVISION)
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body(request))))
        .build();

    HttpResponse<InputStream> response;
    try {
      response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new Failure("Could not reach the model provider", 0, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure("Interrupted while calling the model provider", 0, e);
    }

    try (InputStream in = response.body()) {
      cancellation.onCancel(() -> closeQuietly(in));
      if (response.statusCode() != 200) {
        throw new Failure("Model provider answered HTTP " + response.statusCode(),
            response.statusCode(), null);
      }
      readEvents(in, onText, cancellation);
    } catch (IOException e) {
      if (!cancellation.isCancelled()) {
        throw new Failure("The model stream broke", 0, e);
      }
    }
  }

  private Map<String, Object> body(Request request) {
    List<Map<String, Object>> input = new ArrayList<>();
    for (Turn turn : request.turns()) {
      Map<String, Object> step = new LinkedHashMap<>();
      step.put("type", turn.role() == Turn.Role.USER ? "user_input" : "model_output");
      step.put("content", List.of(Map.of("type", "text", "text", turn.text())));
      input.add(step);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.model());
    body.put("system_instruction", request.systemInstruction());
    body.put("input", input);
    body.put("stream", true);
    body.put("store", false);
    body.put("generation_config", Map.of("max_output_tokens", properties.maxOutputTokens()));
    return body;
  }

  private void readEvents(InputStream in, Consumer<String> onText, Cancellation cancellation)
      throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    Map<Integer, String> stepTypes = new HashMap<>();
    StringBuilder data = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      if (cancellation.isCancelled()) {
        return;
      }
      if (line.isEmpty()) {
        if (data.length() > 0) {
          boolean finished = dispatch(data.toString(), stepTypes, onText);
          data.setLength(0);
          if (finished) {
            return;
          }
        }
      } else if (line.startsWith("data:")) {
        if (data.length() > 0) {
          data.append('\n');
        }
        data.append(line.substring(line.startsWith("data: ") ? 6 : 5));
      }
    }
    if (data.length() > 0 && !cancellation.isCancelled()) {
      dispatch(data.toString(), stepTypes, onText);
    }
  }

  /** Returns true once the provider says the stream is over. */
  private boolean dispatch(String payload, Map<Integer, String> stepTypes, Consumer<String> onText) {
    if ("[DONE]".equals(payload.strip())) {
      return true;
    }
    JsonNode event;
    try {
      event = mapper.readTree(payload);
    } catch (JacksonException e) {
      return false;
    }
    switch (event.path("event_type").asString("")) {
      case "step.start" -> stepTypes.put(event.path("index").asInt(-1),
          event.path("step").path("type").asString(""));
      case "step.delta" -> {
        JsonNode delta = event.path("delta");
        if ("model_output".equals(stepTypes.get(event.path("index").asInt(-1)))
            && "text".equals(delta.path("type").asString(""))) {
          String text = delta.path("text").asString("");
          if (!text.isEmpty()) {
            onText.accept(text);
          }
        }
      }
      case "error" -> {
        JsonNode error = event.path("error");
        log.warn("Gemini stream error: code={}", error.path("code").asString(""));
        throw new Failure("The model reported an error ("
            + error.path("code").asString("unknown") + ")");
      }
      case "interaction.completed" -> {
        String status = event.path("interaction").path("status").asString("");
        if (!status.isEmpty() && !"completed".equals(status)) {
          log.warn("Gemini interaction ended with status {}", status);
        }
      }
      default -> {
        // Unknown events (status updates, usage) are skipped on purpose.
      }
    }
    return false;
  }

  private static void closeQuietly(InputStream in) {
    try {
      in.close();
    } catch (IOException e) {
      // Closing only exists to unblock a read; nothing useful to do on failure.
    }
  }
}
