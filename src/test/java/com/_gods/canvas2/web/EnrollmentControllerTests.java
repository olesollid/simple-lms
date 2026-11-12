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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(EnrollmentController.class)
@AutoConfigureMockMvc
class EnrollmentControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private EnrollmentService enrollmentService;

  private static final String REQUEST_BODY = """
      {"email":"student@example.com","role":"STUDENT"}
      """;

  private Enrollment enrollment(Role role) {
    return new Enrollment(new User("student@example.com", "Student Name"),
        new Course("Intro to Testing", "desc", Term.AUTUMN, 2026), role);
  }

  private RequestPostProcessor asTeacher() {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("email", "teacher@example.com"), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void enrollReturns201() throws Exception {
    when(enrollmentService.enroll(any(), any(), any(), anyString()))
        .thenReturn(enrollment(Role.STUDENT));

    mockMvc.perform(post("/api/courses/1/enrollments")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("student@example.com"))
        .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void enrollTwiceReturns409() throws Exception {
    when(enrollmentService.enroll(any(), any(), any(), anyString()))
        .thenThrow(new ConflictException("already enrolled"));

    mockMvc.perform(post("/api/courses/1/enrollments")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void enrollWithInvalidRoleReturns400() throws Exception {
    mockMvc.perform(post("/api/courses/1/enrollments")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"student@example.com\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getEnrollmentsReturnsAllEnrollments() throws Exception {
    when(enrollmentService.getEnrollmentsByCourse(1L))
        .thenReturn(List.of(enrollment(Role.STUDENT)));

    mockMvc.perform(get("/api/courses/1/enrollments").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("student@example.com"));
  }

  @Test
  void getEnrollmentsFiltersByRole() throws Exception {
    when(enrollmentService.getEnrollmentsByCourse(1L, Role.TEACHER))
        .thenReturn(List.of(enrollment(Role.TEACHER)));

    mockMvc.perform(get("/api/courses/1/enrollments").param("role", "TEACHER").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].role").value("TEACHER"));
  }

  @Test
  void getEnrollmentReturns404WhenNotEnrolled() throws Exception {
    when(enrollmentService.getEnrollment(1L, "student@example.com"))
        .thenThrow(new NotFoundException("not enrolled"));

    mockMvc.perform(get("/api/courses/1/enrollments/student@example.com").with(asTeacher()))
        .andExpect(status().isNotFound());
  }

  @Test
  void changeRoleReturnsUpdatedEnrollment() throws Exception {
    when(enrollmentService.changeRole(eq(1L), eq("student@example.com"), eq(Role.TEACHER),
        anyString())).thenReturn(enrollment(Role.TEACHER));

    mockMvc.perform(put("/api/courses/1/enrollments/student@example.com")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"TEACHER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TEACHER"));
  }

  @Test
  void unenrollReturns204() throws Exception {
    mockMvc.perform(delete("/api/courses/1/enrollments/student@example.com").with(asTeacher()))
        .andExpect(status().isNoContent());
  }

  @Test
  void unenrollReturns404WhenNotEnrolled() throws Exception {
    doThrow(new NotFoundException("not enrolled"))
        .when(enrollmentService).unenroll(eq(1L), eq("student@example.com"), anyString());

    mockMvc.perform(delete("/api/courses/1/enrollments/student@example.com").with(asTeacher()))
        .andExpect(status().isNotFound());
  }
}
