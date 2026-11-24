package com._gods.simplelms.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.service.CourseHasDependentsException;
import com._gods.simplelms.service.CourseNotFoundException;
import com._gods.simplelms.service.CourseService;
import com._gods.simplelms.service.ForbiddenException;
import com._gods.simplelms.web.CourseController;

@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc
class CourseControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CourseService courseService;

  private static final String REQUEST_BODY = """
      {"title":"Intro to Testing","description":"desc","term":"AUTUMN","year":2026}
      """;

  private RequestPostProcessor asTeacher() {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("email", "teacher@example.com"), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  private RequestPostProcessor asStudent() {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("email", "student@example.com"), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void createCourseReturns201() throws Exception {
    Course saved = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(courseService.createCourse(any(), any(), any(), anyInt(), anyString())).thenReturn(saved);

    mockMvc.perform(post("/api/courses")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Intro to Testing"));
  }

  @Test
  void getAllCoursesReturnsExistingCourses() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(courseService.getMyCourses(anyString())).thenReturn(List.of(course));

    mockMvc.perform(get("/api/courses").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Intro to Testing"));
  }

  @Test
  void getCourseByIdReturns404WhenMissing() throws Exception {
    when(courseService.getCourseById(eq(999L), anyString()))
        .thenThrow(new CourseNotFoundException(999L));

    mockMvc.perform(get("/api/courses/999").with(asTeacher()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateCourseReturnsUpdatedCourse() throws Exception {
    Course updated = new Course("Advanced Testing", "new desc", Term.SPRING, 2027);
    when(courseService.updateCourse(any(), any(), any(), any(), anyInt(), anyString()))
        .thenReturn(updated);

    mockMvc.perform(put("/api/courses/1")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Advanced Testing"));
  }

  @Test
  void deleteCourseReturns204() throws Exception {
    mockMvc.perform(delete("/api/courses/1").with(asTeacher()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteCourseWithDependentsReturns409() throws Exception {
    doThrow(new CourseHasDependentsException(1L))
        .when(courseService).deleteCourse(any(), anyString());

    mockMvc.perform(delete("/api/courses/1").with(asTeacher()))
        .andExpect(status().isConflict());
  }

  @Test
  void updateCourseReturns403ForStudent() throws Exception {
    when(courseService.updateCourse(any(), any(), any(), any(), anyInt(), anyString()))
        .thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(put("/api/courses/1")
            .with(asStudent())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteCourseReturns403ForStudent() throws Exception {
    doThrow(new ForbiddenException("not a teacher"))
        .when(courseService).deleteCourse(any(), anyString());

    mockMvc.perform(delete("/api/courses/1").with(asStudent()))
        .andExpect(status().isForbidden());
  }

  @Test
  void createCourseWithInvalidDataReturns400() throws Exception {
    when(courseService.createCourse(any(), any(), any(), anyInt(), anyString()))
        .thenThrow(new IllegalArgumentException("Title must not be blank"));

    mockMvc.perform(post("/api/courses")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isBadRequest());
  }
}
