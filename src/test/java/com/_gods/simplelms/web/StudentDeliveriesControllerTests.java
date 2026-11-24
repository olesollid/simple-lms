package com._gods.simplelms.web;

import static org.mockito.Mockito.when;
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

import com._gods.simplelms.service.ForbiddenException;
import com._gods.simplelms.service.StudentDeliveriesService;
import com._gods.simplelms.web.StudentDeliveriesController;
import com._gods.simplelms.web.dto.StudentDeliveriesResponse;

@WebMvcTest(StudentDeliveriesController.class)
class StudentDeliveriesControllerTests {
  @Autowired MockMvc mvc;
  @MockitoBean StudentDeliveriesService service;

  @Test
  void returnsTeacherSnapshotAndRejectsStudentAccess() throws Exception {
    when(service.getDeliveries(1L, "alice@example.com", "teacher@example.com"))
        .thenReturn(new StudentDeliveriesResponse(1L, "DAT250", "alice@example.com", "Alice", List.of()));
    when(service.getDeliveries(1L, "alice@example.com", "student@example.com"))
        .thenThrow(new ForbiddenException("Only a teacher may review deliveries"));
    for (String requester : List.of("teacher@example.com", "student@example.com")) {
      var request = get("/api/courses/1/students/alice@example.com/deliveries")
          .with(oauth2Login().oauth2User(new DefaultOAuth2User(
              Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", requester), "email")));
      if (requester.startsWith("teacher")) {
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value("Alice"));
      } else {
        mvc.perform(request).andExpect(status().isForbidden());
      }
    }
  }
}
