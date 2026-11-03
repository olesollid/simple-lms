package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.service.CourseHasDependentsException;
import com._gods.canvas2.service.CourseNotFoundException;
import com._gods.canvas2.service.CourseService;

@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourseControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CourseService courseService;

  private static final String REQUEST_BODY = """
      {"title":"Intro to Testing","description":"desc","term":"AUTUMN","year":2026}
      """;

  @Test
  void createCourseReturns201() throws Exception {
    Course saved = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(courseService.createCourse(any(), any(), any(), anyInt())).thenReturn(saved);

    mockMvc.perform(post("/api/courses")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Intro to Testing"));
  }

  @Test
  void getAllCoursesReturnsExistingCourses() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(courseService.getAllCourses()).thenReturn(List.of(course));

    mockMvc.perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Intro to Testing"));
  }

  @Test
  void getCourseByIdReturns404WhenMissing() throws Exception {
    when(courseService.getCourseById(999L)).thenThrow(new CourseNotFoundException(999L));

    mockMvc.perform(get("/api/courses/999"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateCourseReturnsUpdatedCourse() throws Exception {
    Course updated = new Course("Advanced Testing", "new desc", Term.SPRING, 2027);
    when(courseService.updateCourse(any(), any(), any(), any(), anyInt())).thenReturn(updated);

    mockMvc.perform(put("/api/courses/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Advanced Testing"));
  }

  @Test
  void deleteCourseReturns204() throws Exception {
    mockMvc.perform(delete("/api/courses/1"))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteCourseWithDependentsReturns409() throws Exception {
    org.mockito.Mockito.doThrow(new CourseHasDependentsException(1L))
        .when(courseService).deleteCourse(1L);

    mockMvc.perform(delete("/api/courses/1"))
        .andExpect(status().isConflict());
  }

  @Test
  void createCourseWithInvalidDataReturns400() throws Exception {
    when(courseService.createCourse(any(), any(), any(), anyInt()))
        .thenThrow(new IllegalArgumentException("Title must not be blank"));

    mockMvc.perform(post("/api/courses")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isBadRequest());
  }
}
