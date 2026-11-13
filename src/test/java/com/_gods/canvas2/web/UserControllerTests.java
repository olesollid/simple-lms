package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.service.ConflictException;
import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
class UserControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private EnrollmentService enrollmentService;

  private User user() {
    return new User("student@example.com", "Student Name");
  }

  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void getUserReturns404WhenMissing() throws Exception {
    when(userService.getUserByEmail("missing@example.com"))
        .thenThrow(new NotFoundException("User not found"));

    mockMvc.perform(get("/api/users/missing@example.com").with(as("missing@example.com")))
        .andExpect(status().isNotFound());
  }

  @Test
  void getUserForAnotherEmailReturns403() throws Exception {
    mockMvc.perform(get("/api/users/other@example.com").with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEnrollmentsByUserForAnotherEmailReturns403() throws Exception {
    doThrow(new com._gods.canvas2.service.ForbiddenException("not self"))
        .when(enrollmentService).getEnrollmentsByUser(eq("other@example.com"), anyString());

    mockMvc.perform(get("/api/users/other@example.com/enrollments")
            .with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getCurrentUserReturnsAuthenticatedUser() throws Exception {
    when(userService.getUserByEmail("student@example.com")).thenReturn(user());

    mockMvc.perform(get("/api/users/me").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("student@example.com"));
  }

  @Test
  void updateUserReturnsUpdatedUser() throws Exception {
    when(userService.updateUserName(any(), any(), anyString()))
        .thenReturn(new User("student@example.com", "New Name"));

    mockMvc.perform(put("/api/users/student@example.com")
            .with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void deleteUserReturns204() throws Exception {
    mockMvc.perform(delete("/api/users/student@example.com").with(as("student@example.com")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteUserWithDependentsReturns409() throws Exception {
    doThrow(new ConflictException("has dependents"))
        .when(userService).deleteUser(eq("student@example.com"), anyString());

    mockMvc.perform(delete("/api/users/student@example.com").with(as("student@example.com")))
        .andExpect(status().isConflict());
  }

  @Test
  void getEnrollmentsByUserReturnsEnrollments() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(enrollmentService.getEnrollmentsByUser("student@example.com", "student@example.com"))
        .thenReturn(List.of(new Enrollment(user(), course, Role.STUDENT)));

    mockMvc.perform(get("/api/users/student@example.com/enrollments")
            .with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].courseTitle").value("Intro to Testing"))
        .andExpect(jsonPath("$[0].role").value("STUDENT"));
  }
}
