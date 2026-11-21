package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
            Map.of(7L, "Count on your fingers"), 1));

    mockMvc.perform(get("/api/quizzes/5/attempts/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attemptsUsed").value(1))
        .andExpect(jsonPath("$.maxAttempts").value(0))
        .andExpect(jsonPath("$.deadline").value("2026-12-01T12:00:00"))
        .andExpect(jsonPath("$.closed").value(false))
        .andExpect(jsonPath("$.resultsVisible").value(true))
        .andExpect(jsonPath("$.best.score").value(1.0))
        .andExpect(jsonPath("$.latest.incorrectQuestionIds").isEmpty())
        .andExpect(jsonPath("$.hints.7").value("Count on your fingers"))
        .andExpect(jsonPath("$.resultsVisible").value(true))
        .andExpect(jsonPath("$.attemptsGraded").value(1));
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

  @Test
  void aStudentSeesNoScoresWhileResultsAreHiddenButStillSeesHowManyAttemptsAreGraded() throws Exception {
    when(quizSubmissionService.getOwnAttempts(5L, "student@example.com"))
        .thenReturn(new QuizAttemptsResponse(1, 3, null, false, false, null, null, Map.of(), 1));

    mockMvc.perform(get("/api/quizzes/5/attempts/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultsVisible").value(false))
        .andExpect(jsonPath("$.attemptsGraded").value(1))
        .andExpect(jsonPath("$.best").isEmpty())
        .andExpect(jsonPath("$.latest").isEmpty());
  }

  @Test
  void theGradeEndpointIs404WhileResultsAreHidden() throws Exception {
    when(quizSubmissionService.getOwnAttempts(5L, "student@example.com"))
        .thenReturn(new QuizAttemptsResponse(1, 3, null, false, false, null, null, Map.of(), 1));

    mockMvc.perform(get("/api/quizzes/5/grade").with(as("student@example.com")))
        .andExpect(status().isNotFound());
  }

  @Test
  void teachersChangeTheAttemptLimitAndGetTheQuizBack() throws Exception {
    Quiz quiz = new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 3, false);
    when(quizService.updateMaxAttempts(eq(5L), eq(3), eq("teacher@example.com"))).thenReturn(quiz);

    mockMvc.perform(put("/api/quizzes/5/settings").with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"maxAttempts":3}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxAttempts").value(3))
        .andExpect(jsonPath("$.showResultsImmediately").value(true))
        .andExpect(jsonPath("$.resultsPublished").value(false));
  }

  @Test
  void theAttemptLimitIsRequired() throws Exception {
    mockMvc.perform(put("/api/quizzes/5/settings").with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    verify(quizService, never()).updateMaxAttempts(anyLong(), anyInt(), anyString());
  }

  @Test
  void studentsCannotChangeQuizSettings() throws Exception {
    when(quizService.updateMaxAttempts(eq(5L), eq(9), eq("student@example.com")))
        .thenThrow(new ForbiddenException("Only a teacher may change this"));

    mockMvc.perform(put("/api/quizzes/5/settings").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"maxAttempts\":9}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anOrderingQuestionIsAddedWithItsTypeAndComesBackWithIt() throws Exception {
    Quiz quiz = new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 1, false);
    Question question = quiz.addQuestion("Order the steps");
    question.setType(Question.Type.ORDERING);
    question.addOption("Plan", true);
    question.addOption("Build", true);
    when(quizService.addQuestion(eq(5L), any(), eq("teacher@example.com"))).thenReturn(question);

    mockMvc.perform(post("/api/quizzes/5/questions").with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"text":"Order the steps","hint":null,"type":"ORDERING",
                 "options":[{"text":"Plan","correct":false},{"text":"Build","correct":false}]}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("ORDERING"));

    org.mockito.ArgumentCaptor<com._gods.canvas2.web.dto.QuestionRequest> sent =
        org.mockito.ArgumentCaptor.forClass(com._gods.canvas2.web.dto.QuestionRequest.class);
    verify(quizService).addQuestion(eq(5L), sent.capture(), eq("teacher@example.com"));
    org.junit.jupiter.api.Assertions.assertEquals(Question.Type.ORDERING, sent.getValue().type());
  }

  @Test
  void aQuestionWithoutATypeIsSentAsAChoiceQuestion() throws Exception {
    Quiz quiz = new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 1, false);
    Question question = quiz.addQuestion("2+2?");
    question.addOption("4", true);
    question.addOption("5", false);
    when(quizService.addQuestion(eq(5L), any(), anyString())).thenReturn(question);

    mockMvc.perform(post("/api/quizzes/5/questions").with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"text":"2+2?","hint":null,"options":[{"text":"4","correct":true},{"text":"5","correct":false}]}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("CHOICE"));
  }

  @Test
  void studentsNeverGetAnOrderingQuestionsOptionsInTheCorrectOrder() {
    Quiz quiz = new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 1, false);
    Question question = quiz.addQuestion("Order the steps");
    question.setType(Question.Type.ORDERING);
    for (String step : List.of("One", "Two", "Three", "Four", "Five")) {
      question.addOption(step, true);
    }
    List<String> authored = List.of("One", "Two", "Three", "Four", "Five");

    for (int i = 0; i < 200; i++) {
      List<String> shown = com._gods.canvas2.web.dto.QuestionResponse.from(question).options().stream()
          .map(com._gods.canvas2.web.dto.AnswerOptionResponse::text).toList();
      org.junit.jupiter.api.Assertions.assertNotEquals(authored, shown);
      org.junit.jupiter.api.Assertions.assertEquals(authored.stream().sorted().toList(),
          shown.stream().sorted().toList());
    }
  }

  @Test
  void aChoiceQuestionKeepsItsAuthoredOrderAndTypeForStudents() {
    Quiz quiz = new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 1, false);
    Question question = quiz.addQuestion("2+2?");
    question.addOption("3", false);
    question.addOption("4", true);

    var response = com._gods.canvas2.web.dto.QuestionResponse.from(question);

    org.junit.jupiter.api.Assertions.assertEquals(Question.Type.CHOICE, response.type());
    org.junit.jupiter.api.Assertions.assertEquals(List.of("3", "4"),
        response.options().stream().map(com._gods.canvas2.web.dto.AnswerOptionResponse::text).toList());
  }

  private static Quiz newQuiz() {
    return new Quiz(new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1"),
        "Quiz", 0, 1, false);
  }

  private static Question matchingQuestion() {
    Question question = newQuiz().addQuestion("Match the capitals");
    question.setType(Question.Type.MATCHING);
    String[][] pairs = {{"Norway", "Oslo"}, {"France", "Paris"}, {"Spain", "Madrid"},
        {"Italy", "Rome"}, {"Greece", "Athens"}};
    for (int i = 0; i < pairs.length; i++) {
      question.addOption(pairs[i][0], true, pairs[i][1], i);
    }
    return question;
  }

  @Test
  void studentsGetMatchingLeftItemsInOrderAndTheRightItemsShuffled() {
    Question question = matchingQuestion();
    List<String> authoredRight = List.of("Oslo", "Paris", "Madrid", "Rome", "Athens");

    for (int i = 0; i < 200; i++) {
      var response = com._gods.canvas2.web.dto.QuestionResponse.from(question);

      org.junit.jupiter.api.Assertions.assertEquals(Question.Type.MATCHING, response.type());
      org.junit.jupiter.api.Assertions.assertEquals(
          List.of("Norway", "France", "Spain", "Italy", "Greece"),
          response.options().stream().map(com._gods.canvas2.web.dto.AnswerOptionResponse::text).toList());
      // Left-hand items are identified by position, so their ids say nothing about the pairing.
      org.junit.jupiter.api.Assertions.assertEquals(List.of(0L, 1L, 2L, 3L, 4L),
          response.options().stream().map(com._gods.canvas2.web.dto.AnswerOptionResponse::id).toList());
      List<String> shownRight = response.matches().stream()
          .map(com._gods.canvas2.web.dto.AnswerOptionResponse::text).toList();
      org.junit.jupiter.api.Assertions.assertNotEquals(authoredRight, shownRight);
      org.junit.jupiter.api.Assertions.assertEquals(authoredRight.stream().sorted().toList(),
          shownRight.stream().sorted().toList());
    }
  }

  @Test
  void studentsGetNoOptionsOrAcceptedAnswersForAFillInQuestion() throws Exception {
    Question question = newQuiz().addQuestion("The capital of ___ is ___.");
    question.setType(Question.Type.FILL_IN);
    question.addOption("Norway|Norge", true, null, 0);
    question.addOption("Oslo", true, null, 1);
    when(quizService.getQuestions(5L, "student@example.com")).thenReturn(List.of(question));

    String body = mockMvc.perform(get("/api/quizzes/5/questions").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("FILL_IN"))
        .andExpect(jsonPath("$[0].text").value("The capital of ___ is ___."))
        .andExpect(jsonPath("$[0].options").isEmpty())
        .andExpect(jsonPath("$[0].matches").isEmpty())
        .andReturn().getResponse().getContentAsString();

    org.junit.jupiter.api.Assertions.assertFalse(body.contains("Norge"));
    org.junit.jupiter.api.Assertions.assertFalse(body.contains("Oslo"));
  }

  @Test
  void studentsNeverSeeWhichRightHandItemBelongsWhereInTheJson() throws Exception {
    Question question = matchingQuestion();
    when(quizService.getQuestions(5L, "student@example.com")).thenReturn(List.of(question));

    mockMvc.perform(get("/api/quizzes/5/questions").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("MATCHING"))
        .andExpect(jsonPath("$[0].options[0].text").value("Norway"))
        .andExpect(jsonPath("$[0].options[0].matchText").doesNotExist())
        .andExpect(jsonPath("$[0].options[0].correct").doesNotExist())
        .andExpect(jsonPath("$[0].matches.length()").value(5))
        .andExpect(jsonPath("$[0].matches[0].matchText").doesNotExist());
  }

  @Test
  void teachersSeeMatchingPairsAndFillInAnswersInTheOrderEntered() throws Exception {
    Question pairs = matchingQuestion();
    Question blank = newQuiz().addQuestion("Capital of ___");
    blank.setType(Question.Type.FILL_IN);
    blank.addOption("Norway|Norge", true, null, 0);
    when(quizService.getQuestionsWithAnswers(5L, "teacher@example.com"))
        .thenReturn(List.of(pairs, blank));

    mockMvc.perform(get("/api/quizzes/5/questions/with-answers").with(as("teacher@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("MATCHING"))
        .andExpect(jsonPath("$[0].options[0].text").value("Norway"))
        .andExpect(jsonPath("$[0].options[0].matchText").value("Oslo"))
        .andExpect(jsonPath("$[0].options[4].matchText").value("Athens"))
        .andExpect(jsonPath("$[1].type").value("FILL_IN"))
        .andExpect(jsonPath("$[1].options[0].text").value("Norway|Norge"));
  }

  @Test
  void aMatchingQuestionIsAddedWithItsPairs() throws Exception {
    when(quizService.addQuestion(eq(5L), any(), eq("teacher@example.com"))).thenReturn(matchingQuestion());

    mockMvc.perform(post("/api/quizzes/5/questions").with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"text":"Match the capitals","type":"MATCHING",
                 "options":[{"text":"Norway","correct":false,"matchText":"Oslo"},
                            {"text":"France","correct":false,"matchText":"Paris"}]}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("MATCHING"));

    org.mockito.ArgumentCaptor<com._gods.canvas2.web.dto.QuestionRequest> sent =
        org.mockito.ArgumentCaptor.forClass(com._gods.canvas2.web.dto.QuestionRequest.class);
    verify(quizService).addQuestion(eq(5L), sent.capture(), eq("teacher@example.com"));
    org.junit.jupiter.api.Assertions.assertEquals(Question.Type.MATCHING, sent.getValue().type());
    org.junit.jupiter.api.Assertions.assertEquals("Oslo", sent.getValue().options().getFirst().matchText());
  }

  @Test
  void typedAnswersReachTheSubmissionService() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), eq("student@example.com"))).thenReturn(1);

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"answers":{"1":[10]},"textAnswers":{"2":["Norway","Oslo"]}}
                """))
        .andExpect(status().isAccepted());

    org.mockito.ArgumentCaptor<com._gods.canvas2.web.dto.QuizSubmissionRequest> sent =
        org.mockito.ArgumentCaptor.forClass(com._gods.canvas2.web.dto.QuizSubmissionRequest.class);
    verify(quizSubmissionService).submit(eq(5L), sent.capture(), eq("student@example.com"));
    org.junit.jupiter.api.Assertions.assertEquals(Map.of(2L, List.of("Norway", "Oslo")),
        sent.getValue().textAnswers());
    org.junit.jupiter.api.Assertions.assertEquals(Map.of(1L, List.of(10L)), sent.getValue().answers());
  }

  @Test
  void aSubmissionWithoutTypedAnswersStillWorks() throws Exception {
    when(quizSubmissionService.submit(eq(5L), any(), eq("student@example.com"))).thenReturn(1);

    mockMvc.perform(post("/api/quizzes/5/submissions").with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
        .andExpect(status().isAccepted());

    org.mockito.ArgumentCaptor<com._gods.canvas2.web.dto.QuizSubmissionRequest> sent =
        org.mockito.ArgumentCaptor.forClass(com._gods.canvas2.web.dto.QuizSubmissionRequest.class);
    verify(quizSubmissionService).submit(eq(5L), sent.capture(), eq("student@example.com"));
    org.junit.jupiter.api.Assertions.assertEquals(null, sent.getValue().textAnswers());
  }
}
