package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.service.ContentNotFoundException;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.ModuleNotFoundException;
import com._gods.canvas2.service.StudentInteractionService;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc
class ContentControllerTests {
  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }
  @Test
  void updatesHandInPolicyAndRejectsNonTeachers() throws Exception {
    var deadline = java.time.LocalDateTime.of(2026, 11, 30, 12, 0);
    Assignment assignment = assignment(1L, module(1L), "Essay", "Write");
    assignment.updateSubmissionPolicy(deadline, true);
    when(contentService.updateSubmissionPolicy(1L, deadline, true, "teacher@example.com"))
        .thenReturn(assignment);
    when(contentService.updateSubmissionPolicy(1L, deadline, true, "student@example.com"))
        .thenThrow(new com._gods.canvas2.service.ForbiddenException("not a teacher"));
    String body = "{\"deadline\":\"2026-11-30T12:00:00\",\"lateSubmissionsAccepted\":true}";
    mockMvc.perform(put("/api/contents/1/submission-policy").with(as("teacher@example.com"))
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadline").value("2026-11-30T12:00:00"))
        .andExpect(jsonPath("$.lateSubmissionsAccepted").value(true));
    mockMvc.perform(put("/api/contents/1/submission-policy").with(as("student@example.com"))
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
  }

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ContentService contentService;

  @MockitoBean
  private StudentInteractionService studentInteractionService;

  private Module module(Long id) {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1");
    setField(module, "id", id);
    return module;
  }

  private StaticPage page(Long id, Module module, String title, String body) {
    StaticPage page = new StaticPage(module, title, 0, body);
    setField(page, "id", id);
    return page;
  }

  private Assignment assignment(Long id, Module module, String title, String instructions) {
    Assignment assignment = new Assignment(module, title, 0, instructions);
    setField(assignment, "id", id);
    return assignment;
  }

  private CodingExercise codingExercise(Long id, Module module, String title, String repoUrl) {
    CodingExercise codingExercise = new CodingExercise(module, title, 0, repoUrl);
    setField(codingExercise, "id", id);
    return codingExercise;
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

  private RequestPostProcessor asTeacher() {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("email", "teacher@example.com"), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void createStaticPageReturns201() throws Exception {
    Module module = module(1L);
    StaticPage page = page(1L, module, "Syllabus", "Welcome!");
    when(contentService.createStaticPage(anyLong(), any(), anyInt(), any(), any(), any(),
        anyString())).thenReturn(page);

    mockMvc.perform(post("/api/modules/1/pages")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Syllabus","position":0,"body":"Welcome!"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Syllabus"))
        .andExpect(jsonPath("$.body").value("Welcome!"));
  }

  @Test
  void createStaticPageReturns404WhenModuleMissing() throws Exception {
    when(contentService.createStaticPage(anyLong(), any(), anyInt(), any(), any(), any(),
        anyString())).thenThrow(new ModuleNotFoundException(999L));

    mockMvc.perform(post("/api/modules/999/pages")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Syllabus","position":0,"body":"Welcome!"}
                """))
        .andExpect(status().isNotFound());
  }

  /** Exactly the body the frontend sends: showHints is left out, which means on. */
  @Test
  void createQuizReturns201AsADraft() throws Exception {
    Module module = module(1L);
    Quiz quiz = new Quiz(module, "Week 1 quiz", 0, 0, false);
    setField(quiz, "id", 1L);
    when(contentService.createQuiz(anyLong(), any(), anyInt(), anyInt(), anyBoolean(), any(),
        anyString())).thenReturn(quiz);

    mockMvc.perform(post("/api/modules/1/quizzes")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Week 1 quiz","position":0,"maxAttempts":0,"deadline":null}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("Quiz"))
        .andExpect(jsonPath("$.maxAttempts").value(0))
        .andExpect(jsonPath("$.published").value(false))
        .andExpect(jsonPath("$.closed").value(false));
  }

  @Test
  void createAssignmentReturns201() throws Exception {
    Module module = module(1L);
    Assignment assignment = assignment(1L, module, "Essay", "Write 500 words");
    when(contentService.createAssignment(anyLong(), any(), anyInt(), any(), any(), anyBoolean(),
        anyBoolean(), anyString())).thenReturn(assignment);

    mockMvc.perform(post("/api/modules/1/assignments")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Essay","position":0,"instructions":"Write 500 words",
                 "deadline":null,"lateSubmissionsAccepted":false,"groupSubmission":false}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Essay"))
        .andExpect(jsonPath("$.instructions").value("Write 500 words"));
  }

  @Test
  void createAssignmentReturns403ForStudent() throws Exception {
    when(contentService.createAssignment(anyLong(), any(), anyInt(), any(), any(), anyBoolean(),
        anyBoolean(), anyString())).thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(post("/api/modules/1/assignments")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Essay","position":0,"instructions":"Write 500 words",
                 "deadline":null,"lateSubmissionsAccepted":false,"groupSubmission":false}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void createCodingExerciseReturns201() throws Exception {
    Module module = module(1L);
    CodingExercise codingExercise = codingExercise(1L, module, "Fix the bug",
        "https://example.com/repo");
    when(contentService.createCodingExercise(anyLong(), any(), anyInt(), any(), any(),
        anyBoolean(), anyBoolean(), anyString())).thenReturn(codingExercise);

    mockMvc.perform(post("/api/modules/1/coding-exercises")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Fix the bug","position":0,"repositoryUrl":"https://example.com/repo",
                 "deadline":null,"lateSubmissionsAccepted":false,"groupSubmission":false}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Fix the bug"))
        .andExpect(jsonPath("$.repositoryUrl").value("https://example.com/repo"));
  }

  @Test
  void createCodingExerciseReturns403ForStudent() throws Exception {
    when(contentService.createCodingExercise(anyLong(), any(), anyInt(), any(), any(),
        anyBoolean(), anyBoolean(), anyString())).thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(post("/api/modules/1/coding-exercises")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Fix the bug","position":0,"repositoryUrl":"https://example.com/repo",
                 "deadline":null,"lateSubmissionsAccepted":false,"groupSubmission":false}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getContentsByModuleReturnsExistingContents() throws Exception {
    Module module = module(1L);
    StaticPage page = page(1L, module, "Syllabus", "Welcome!");
    when(contentService.getContentsByModule(eq(1L), anyString())).thenReturn(List.of(page));

    mockMvc.perform(get("/api/modules/1/contents").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Syllabus"));
  }

  @Test
  void getContentByIdReturns404WhenMissing() throws Exception {
    when(contentService.getContentById(eq(999L), anyString()))
        .thenThrow(new ContentNotFoundException(999L));

    mockMvc.perform(get("/api/contents/999").with(asTeacher()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateContentReturnsUpdatedContent() throws Exception {
    Module module = module(1L);
    StaticPage updated = page(1L, module, "Updated", "Welcome!");
    when(contentService.updateContentMetadata(any(), any(), anyInt(), any(), any(), anyString()))
        .thenReturn(updated);

    mockMvc.perform(put("/api/contents/1")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Updated","position":0}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated"));
  }

  @Test
  void updateStaticPageBodyReturnsUpdatedContent() throws Exception {
    Module module = module(1L);
    StaticPage updated = page(1L, module, "Syllabus", "Updated body");
    when(contentService.updateStaticPageBody(any(), any(), anyString())).thenReturn(updated);

    mockMvc.perform(put("/api/contents/1/body")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"body":"Updated body"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Updated body"));
  }

  @Test
  void deleteContentReturns204() throws Exception {
    mockMvc.perform(delete("/api/contents/1").with(asTeacher()))
        .andExpect(status().isNoContent());
  }

  @Test
  void updatesTheTextOfAContentItem() throws Exception {
    StaticPage page = page(1L, module(1L), "Syllabus", "New body");
    when(contentService.updateText(1L, "New body", "teacher@example.com")).thenReturn(page);

    mockMvc.perform(put("/api/contents/1/text")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"New body\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("New body"))
        .andExpect(jsonPath("$.visibleToStudents").value(true));
  }

  @Test
  void reorderContentsRejectsAnIncompleteOrderWith400() throws Exception {
    when(contentService.reorderContents(1L, List.of(5L), "teacher@example.com"))
        .thenThrow(new IllegalArgumentException("The new order must list each of the content items exactly once"));

    mockMvc.perform(put("/api/modules/1/contents/order")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"ids\":[5]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The new order must list each of the content items exactly once"));
  }

  @Test
  void listsTheRequestersOwnCompletionsInACourse() throws Exception {
    StaticPage page = page(1L, module(1L), "Syllabus", "Welcome!");
    StudentInteraction interaction = new StudentInteraction(new User("teacher@example.com", "Teacher"), page);
    interaction.markCompleted();
    when(studentInteractionService.getOwnCompletions(1L, "teacher@example.com"))
        .thenReturn(List.of(interaction));

    mockMvc.perform(get("/api/courses/1/interactions/mine").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].contentId").value(1))
        .andExpect(jsonPath("$[0].completedAt").exists());
  }

  @Test
  void markCompletedReturnsTheInteraction() throws Exception {
    Module module = module(1L);
    StaticPage page = page(1L, module, "Syllabus", "Welcome!");
    User user = new User("teacher@example.com", "Teacher");
    StudentInteraction interaction = new StudentInteraction(user, page);
    interaction.markCompleted();
    when(studentInteractionService.markCompleted(eq(1L), anyString())).thenReturn(interaction);

    mockMvc.perform(post("/api/contents/1/interactions").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentId").value(1))
        .andExpect(jsonPath("$.userEmail").value("teacher@example.com"))
        .andExpect(jsonPath("$.completedAt").exists());
  }

  @Test
  void markCompletedReturns404WhenContentMissing() throws Exception {
    when(studentInteractionService.markCompleted(eq(999L), anyString()))
        .thenThrow(new ContentNotFoundException(999L));

    mockMvc.perform(post("/api/contents/999/interactions").with(asTeacher()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createQuizTurnsHintsOnWhenTheRequestDoesNotSay() throws Exception {
    Quiz quiz = new Quiz(module(1L), "Quiz", 0, 0, true);
    setField(quiz, "id", 1L);
    when(contentService.createQuiz(anyLong(), any(), anyInt(), anyInt(), anyBoolean(), any(),
        anyString())).thenReturn(quiz);

    mockMvc.perform(post("/api/modules/1/quizzes").with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Quiz\",\"position\":0,\"maxAttempts\":0,\"deadline\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.showHints").value(true));

    verify(contentService).createQuiz(eq(1L), eq("Quiz"), eq(0), eq(0), eq(true), any(), anyString());
  }

  @Test
  void createQuizHonoursAnExplicitShowHintsFalse() throws Exception {
    Quiz quiz = new Quiz(module(1L), "Quiz", 0, 2, false);
    setField(quiz, "id", 1L);
    when(contentService.createQuiz(anyLong(), any(), anyInt(), anyInt(), anyBoolean(), any(),
        anyString())).thenReturn(quiz);

    mockMvc.perform(post("/api/modules/1/quizzes").with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Quiz\",\"position\":0,\"maxAttempts\":2,\"deadline\":null,\"showHints\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.showHints").value(false));

    verify(contentService).createQuiz(eq(1L), eq("Quiz"), eq(0), eq(2), eq(false), any(), anyString());
  }
}
