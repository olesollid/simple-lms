package com._gods.simplelms.aitutor;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com._gods.simplelms.aitutor.TutorModelClient.Cancellation;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/ai-tutor")
public class AiTutorController {

  private static final long STREAM_TIMEOUT_MS = 120_000;

  private final TutorAccessService accessService;
  private final TutorChatService chatService;
  private final TutorModelClient modelClient;
  private final ObjectMapper mapper;

  public AiTutorController(TutorAccessService accessService, TutorChatService chatService,
      TutorModelClient modelClient, ObjectMapper mapper) {
    this.accessService = accessService;
    this.chatService = chatService;
    this.modelClient = modelClient;
    this.mapper = mapper;
  }

  /** Tells the widget whether to show itself. Off unless the server says otherwise. */
  @GetMapping("/config")
  public TutorConfigResponse getConfig(
      @RequestParam(name = "course_id", required = false) Long courseId,
      Authentication authentication) {
    boolean enabled = modelClient.isConfigured()
        && accessService.isEnabledFor(authentication.getName(), courseId);
    return new TutorConfigResponse(enabled);
  }

  /** Streams the tutor's answer as server-sent events (delta, then done, or error). */
  @PostMapping("/chat")
  public ResponseEntity<SseEmitter> chat(@RequestBody TutorChatRequest request,
      Authentication authentication) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    Cancellation cancellation = chatService.start(authentication.getName(), request,
        new TutorEvent.Sink() {
          @Override
          public void send(TutorEvent event) {
            try {
              emitter.send(SseEmitter.event().data(mapper.writeValueAsString(event)));
            } catch (IOException e) {
              throw new IllegalStateException("Client disconnected", e);
            }
          }

          @Override
          public void complete() {
            emitter.complete();
          }
        });
    emitter.onCompletion(cancellation::cancel);
    emitter.onTimeout(() -> {
      cancellation.cancel();
      emitter.complete();
    });
    emitter.onError(error -> cancellation.cancel());
    return ResponseEntity.ok()
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }
}
