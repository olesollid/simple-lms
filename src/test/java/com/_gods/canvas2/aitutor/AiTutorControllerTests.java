package com._gods.canvas2.aitutor;

import com._gods.canvas2.aitutor.TutorModelClient.Cancellation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.web.GlobalExceptionHandler;

@WebMvcTest(AiTutorController.class)
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class AiTutorControllerTests {

  private static final String BODY = """
      {"messages":[{"role":"user","content":"Explain JPA"}],
       "context":{"page_type":"course","course_id":1}}
      """;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TutorAccessService accessService;
  @MockitoBean private TutorChatService chatService;
  @MockitoBean private TutorModelClient modelClient;

  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  // ---- config ----

  @Test
  void reportsEnabledForACourse() throws Exception {
    when(modelClient.isConfigured()).thenReturn(true);
    when(accessService.isEnabledFor("student@example.com", 4L)).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").param("course_id", "4").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void reportsDisabledWhenTheServiceSaysNo() throws Exception {
    when(modelClient.isConfigured()).thenReturn(true);
    when(accessService.isEnabledFor("student@example.com", 4L)).thenReturn(false);

    mockMvc.perform(get("/api/ai-tutor/config").param("course_id", "4").with(as("student@example.com")))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void reportsDisabledWhenNoModelKeyIsConfigured() throws Exception {
    when(modelClient.isConfigured()).thenReturn(false);
    when(accessService.isEnabledFor("student@example.com", 4L)).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").param("course_id", "4").with(as("student@example.com")))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void asksAboutTheDashboardWhenNoCourseIsGiven() throws Exception {
    when(modelClient.isConfigured()).thenReturn(true);
    when(accessService.isEnabledFor(eq("student@example.com"), isNull())).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").with(as("student@example.com")))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void usesTheLoggedInUserNeverAClientSuppliedEmail() throws Exception {
    when(modelClient.isConfigured()).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").param("email", "teacher@example.com")
        .with(as("student@example.com")));

    verify(accessService).isEnabledFor(eq("student@example.com"), isNull());
  }

  // ---- chat ----

  @Test
  void streamsEventsAsServerSentEvents() throws Exception {
    when(chatService.start(eq("student@example.com"), any(), any())).thenAnswer(invocation -> {
      TutorEvent.Sink sink = invocation.getArgument(2);
      sink.send(TutorEvent.delta("Hello"));
      sink.send(TutorEvent.done());
      sink.complete();
      return new Cancellation();
    });

    MvcResult started = mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).accept("text/event-stream, application/json").content(BODY))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(started))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(containsString("data:{\"type\":\"delta\",\"text\":\"Hello\"}")))
        .andExpect(content().string(containsString("data:{\"type\":\"done\"}")));
  }

  @Test
  void passesTheParsedRequestAndTheSessionUserToTheService() throws Exception {
    mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
        .contentType(MediaType.APPLICATION_JSON).content(BODY));

    org.mockito.ArgumentCaptor<TutorChatRequest> captured = org.mockito.ArgumentCaptor.forClass(TutorChatRequest.class);
    verify(chatService).start(eq("student@example.com"), captured.capture(), any());
    TutorChatRequest parsed = captured.getValue();
    assertEqualsText("Explain JPA", parsed.messages().getFirst().content());
    org.junit.jupiter.api.Assertions.assertEquals(TutorPageType.COURSE, parsed.context().pageType());
    org.junit.jupiter.api.Assertions.assertEquals(1L, parsed.context().courseId());
  }

  private static void assertEqualsText(String expected, String actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }

  // A client that accepts ONLY text/event-stream cannot receive the JSON error body (Spring answers
  // with "no acceptable representation"), so the widget must send both types in Accept.
  @Test
  void refusalsAreJsonErrorsWhenTheClientAcceptsJsonAlongsideTheEventStream() throws Exception {
    when(chatService.start(any(), any(), any())).thenThrow(new ForbiddenException("not enrolled"));

    mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).accept("text/event-stream, application/json").content(BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("not enrolled"));
  }

  @Test
  void aRateLimitIs429WithRetryAfter() throws Exception {
    when(chatService.start(any(), any(), any())).thenThrow(new RateLimitExceededException(30));

    mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).accept("text/event-stream, application/json").content(BODY))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.retry_after_seconds").value(30));
  }

  @Test
  void anUnavailableTutorIs503() throws Exception {
    when(chatService.start(any(), any(), any())).thenThrow(new TutorChatService.Unavailable("busy"));

    mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).accept("text/event-stream, application/json").content(BODY))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void aMalformedBodyIs400() throws Exception {
    mockMvc.perform(post("/api/ai-tutor/chat").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).accept("text/event-stream, application/json")
            .content("{\"messages\":[{\"role\":\"wizard\",\"content\":\"hi\"}],\"context\":{}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRequestWithoutCsrfProtectionIsRefused() throws Exception {
    RequestPostProcessor loginOnly = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", "student@example.com"), "email"));

    mockMvc.perform(post("/api/ai-tutor/chat").with(loginOnly)
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isForbidden());
  }
}
