package com._gods.canvas2.aitutor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AiTutorController.class)
@AutoConfigureMockMvc
class AiTutorControllerTests {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TutorAccessService accessService;

  private RequestPostProcessor as(String email) {
    return oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
  }

  @Test
  void reportsEnabledForACourse() throws Exception {
    when(accessService.isEnabledFor("student@example.com", 4L)).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").param("course_id", "4").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void reportsDisabledWhenTheServiceSaysNo() throws Exception {
    when(accessService.isEnabledFor("student@example.com", 4L)).thenReturn(false);

    mockMvc.perform(get("/api/ai-tutor/config").param("course_id", "4").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void asksAboutTheDashboardWhenNoCourseIsGiven() throws Exception {
    when(accessService.isEnabledFor(eq("student@example.com"), isNull())).thenReturn(true);

    mockMvc.perform(get("/api/ai-tutor/config").with(as("student@example.com")))
        .andExpect(jsonPath("$.enabled").value(true));
    verify(accessService).isEnabledFor(eq("student@example.com"), isNull());
  }

  @Test
  void usesTheLoggedInUserNeverAClientSuppliedEmail() throws Exception {
    mockMvc.perform(get("/api/ai-tutor/config").param("email", "teacher@example.com")
        .with(as("student@example.com")));

    verify(accessService).isEnabledFor(eq("student@example.com"), isNull());
  }
}
