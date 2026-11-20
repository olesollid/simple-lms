package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.service.ContentNotFoundException;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.SubmissionService;

@WebMvcTest(SubmissionController.class)
@AutoConfigureMockMvc
class SubmissionControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SubmissionService submissionService;

  private Submission submission(Long id, String submitterEmail, String text) {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1");
    Assignment assignment = new Assignment(module, "Essay", 0, "Write 500 words");
    User submitter = new User(submitterEmail, "Student");
    Submission submission = new Submission(submitter, assignment, text);
    setField(submission, "id", id);
    return submission;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Class<?> type = target.getClass();
      java.lang.reflect.Field field = null;
      while (type != null) {
        try {
          field = type.getDeclaredField(fieldName);
          break;
        } catch (NoSuchFieldException e) {
          type = type.getSuperclass();
        }
      }
      if (field == null) {
        throw new NoSuchFieldException(fieldName);
      }
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void submitReturns201() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    when(submissionService.submit(eq(1L), any(), anyString())).thenReturn(submission);

    mockMvc.perform(post("/api/contents/1/submissions")
            .with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"text":"My essay"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("My essay"))
        .andExpect(jsonPath("$.submitterEmail").value("student@example.com"));
  }

  @Test
  void submitReturns404WhenContentIsLockedOrMissing() throws Exception {
    when(submissionService.submit(eq(999L), any(), anyString()))
        .thenThrow(new ContentNotFoundException(999L));

    mockMvc.perform(post("/api/contents/999/submissions")
            .with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"text":"My essay"}
                """))
        .andExpect(status().isNotFound());
  }

  @Test
  void submitReturns403WhenNotEnrolled() throws Exception {
    when(submissionService.submit(anyLong(), any(), anyString()))
        .thenThrow(new ForbiddenException("not enrolled"));

    mockMvc.perform(post("/api/contents/1/submissions")
            .with(as("stranger@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"text":"My essay"}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSubmissionsByContentReturnsExistingSubmissions() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    when(submissionService.getSubmissionsByContent(eq(1L), anyString()))
        .thenReturn(List.of(submission));

    mockMvc.perform(get("/api/contents/1/submissions").with(as("teacher@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("My essay"));
  }

  @Test
  void getSubmissionsByContentReturns403ForStudent() throws Exception {
    when(submissionService.getSubmissionsByContent(eq(1L), anyString()))
        .thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(get("/api/contents/1/submissions").with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getOwnSubmissionReturnsTheCallersSubmission() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    when(submissionService.getOwnSubmission(eq(1L), anyString())).thenReturn(submission);

    mockMvc.perform(get("/api/contents/1/submissions/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("My essay"));
  }

  @Test
  void gradeReturnsTheGradedSubmission() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    submission.grade(85.5, "Well done");
    when(submissionService.grade(eq(1L), anyDouble(), any(), anyString())).thenReturn(submission);

    mockMvc.perform(put("/api/submissions/1/grade")
            .with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"grade":85.5,"feedback":"Well done"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").value(85.5))
        .andExpect(jsonPath("$.feedback").value("Well done"));
  }

  @Test
  void ownSubmissionHidesGradeAndFeedbackUntilContentIsPublished() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    submission.grade(85, "Private feedback");
    when(submissionService.getOwnSubmission(eq(1L), anyString())).thenReturn(submission);

    mockMvc.perform(get("/api/contents/1/submissions/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").isEmpty())
        .andExpect(jsonPath("$.feedback").isEmpty())
        .andExpect(jsonPath("$.gradedAt").isEmpty())
        .andExpect(jsonPath("$.gradingComplete").value(true));

    submission.getContent().publishGrades();
    mockMvc.perform(get("/api/contents/1/submissions/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").value(85))
        .andExpect(jsonPath("$.feedback").value("Private feedback"));
  }

  @Test
  void teacherListIncludesUnpublishedGrades() throws Exception {
    Submission submission = submission(1L, "student@example.com", "My essay");
    submission.grade(85, "Private feedback");
    when(submissionService.getSubmissionsByContent(eq(1L), anyString())).thenReturn(List.of(submission));
    mockMvc.perform(get("/api/contents/1/submissions").with(as("teacher@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].grade").value(85))
        .andExpect(jsonPath("$[0].feedback").value("Private feedback"));
  }

  @Test
  void publicationReturnsSuccessAndRejectsStudents() throws Exception {
    when(submissionService.publishGrades(1L, "teacher@example.com")).thenReturn(true);
    when(submissionService.publishGrades(1L, "student@example.com"))
        .thenThrow(new ForbiddenException("not a teacher"));
    mockMvc.perform(put("/api/contents/1/grades/publication").with(as("teacher@example.com")))
        .andExpect(status().isOk()).andExpect(content().string("true"));
    mockMvc.perform(put("/api/contents/1/grades/publication").with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void gradeReturns403ForStudent() throws Exception {
    when(submissionService.grade(eq(1L), anyDouble(), any(), anyString()))
        .thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(put("/api/submissions/1/grade")
            .with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"grade":100,"feedback":"Nice try"}
                """))
        .andExpect(status().isForbidden());
  }
}
