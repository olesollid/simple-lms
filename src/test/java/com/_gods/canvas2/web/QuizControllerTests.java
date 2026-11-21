package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com._gods.canvas2.grading.QuizGrade;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.service.ConflictException;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.QuizService;
import com._gods.canvas2.service.QuizSubmissionService;
import com._gods.canvas2.web.dto.QuizAttemptsResponse;
import com._gods.canvas2.web.dto.QuizStudentResultResponse;

@WebMvcTest(QuizController.class)
@AutoConfigureMockMvc
class QuizControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private QuizService quizService;

  @MockitoBean
  private QuizSubmissionService quizSubmissionService;

  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  private static final String ANSWERS = """
      {"answers":{"1":[10,11]}}
      """;

  @Test
  void submitReturns202WithTheAttemptNumber() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), eq("student@example.com"))).thenReturn(2);

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.attemptNumber").value(2));
  }

  @Test
  void submitReturns409WhenAttemptsAreUsedUp() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), anyString()))
        .thenThrow(new ConflictException("You have used all 2 attempts for this quiz"));

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("You have used all 2 attempts for this quiz"));
  }

  @Test
  void submitReturns503WithAClearMessageWhenRabbitMqIsDown() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), anyString()))
        .thenThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")));

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message")
            .value("Quiz grading is temporarily unavailable. Try again in a moment."));
  }

  @Test
  void submitReturns403AfterTheDeadline() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), anyString())).thenThrow(
        new ForbiddenException("The deadline has passed. This quiz no longer accepts attempts."));

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message")
            .value("The deadline has passed. This quiz no longer accepts attempts."));
  }

  @Test
  void returnsTheStudentsOwnAttempts() throws Exception {
    QuizGrade best = new QuizGrade(5L, "student@example.com", 1, 2, 2, 1.0, List.of(),
        LocalDateTime.of(2026, 11, 21, 10, 0));
    when(quizSubmissionService.getOwnAttempts(5L, "student@example.com"))
        .thenReturn(new QuizAttemptsResponse(1, 0, LocalDateTime.of(2026, 12, 1, 12, 0), false, true, best, best,
            Map.of(7L, "Count on your fingers")));

    mockMvc.perform(get("/api/quizzes/5/attempts/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attemptsUsed").value(1))
        .andExpect(jsonPath("$.maxAttempts").value(0))
        .andExpect(jsonPath("$.deadline").value("2026-12-01T12:00:00"))
        .andExpect(jsonPath("$.closed").value(false))
        .andExpect(jsonPath("$.resultsVisible").value(true))
        .andExpect(jsonPath("$.best.score").value(1.0))
        .andExpect(jsonPath("$.latest.incorrectQuestionIds").isEmpty())
        .andExpect(jsonPath("$.hints.7").value("Count on your fingers"));
  }

  @Test
  void questionsForStudentsCarryNeitherCorrectFlagsNorHints() throws Exception {
    Course course = new Course("Intro", "desc", Term.AUTUMN, 2026);
    Quiz quiz = new Quiz(new Module(course, "Week 1"), "Quiz", 0, 1, false);
    Question question = quiz.addQuestion("What is 2+2?", "Count on your fingers");
    question.addOption("4", true);
    question.addOption("5", false);
    when(quizService.getQuestions(5L, "student@example.com")).thenReturn(List.of(question));

    mockMvc.perform(get("/api/quizzes/5/questions").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].text").value("What is 2+2?"))
        .andExpect(jsonPath("$[0].hint").doesNotExist())
        .andExpect(jsonPath("$[0].options[0].text").value("4"))
        .andExpect(jsonPath("$[0].options[0].correct").doesNotExist());
  }

  @Test
  void returnsResultsForTeachersAnd403ForStudents() throws Exception {
    when(quizSubmissionService.getResults(5L, "teacher@example.com")).thenReturn(List.of(
        new QuizStudentResultResponse("ada@example.com", "Ada", 0, null)));
    when(quizSubmissionService.getResults(5L, "student@example.com"))
        .thenThrow(new ForbiddenException("Only a teacher may see quiz results"));

    mockMvc.perform(get("/api/quizzes/5/results").with(as("teacher@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Ada"))
        .andExpect(jsonPath("$[0].best").isEmpty());
    mockMvc.perform(get("/api/quizzes/5/results").with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void publishingAnEmptyQuizReturns409() throws Exception {
    when(quizService.publishQuiz(5L, "teacher@example.com"))
        .thenThrow(new ConflictException("Add at least one question before publishing the quiz"));

    mockMvc.perform(put("/api/quizzes/5/publication").with(as("teacher@example.com")))
        .andExpect(status().isConflict());
  }
}
