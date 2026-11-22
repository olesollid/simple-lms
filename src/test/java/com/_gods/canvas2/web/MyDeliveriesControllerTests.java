package com._gods.canvas2.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.service.MyDeliveriesService;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.web.dto.MyDeliveriesResponse;

@WebMvcTest(MyDeliveriesController.class)
class MyDeliveriesControllerTests {
  @Autowired MockMvc mvc;
  @MockitoBean MyDeliveriesService service;

  @Test
  void usesTheSessionIdentityAndIgnoresAnEmailQueryParameter() throws Exception {
    when(service.getOwnDeliveries(1L, "alice@example.com"))
        .thenReturn(new MyDeliveriesResponse(1L, Role.STUDENT, List.of()));
    mvc.perform(get("/api/courses/1/my-deliveries?email=bob@example.com")
        .with(oauth2Login().oauth2User(new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("email", "alice@example.com"), "email"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    verify(service).getOwnDeliveries(1L, "alice@example.com");
    verifyNoMoreInteractions(service);
  }

  @Test
  void rejectsAnUnenrolledCaller() throws Exception {
    when(service.getOwnDeliveries(1L, "stranger@example.com")).thenThrow(new ForbiddenException("Not enrolled"));
    mvc.perform(get("/api/courses/1/my-deliveries")
        .with(oauth2Login().oauth2User(new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("email", "stranger@example.com"), "email"))))
        .andExpect(status().isForbidden());
  }
}
