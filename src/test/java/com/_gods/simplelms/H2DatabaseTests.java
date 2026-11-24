package com._gods.simplelms;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.model.*;
import com._gods.simplelms.model.Module;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2console")
class H2DatabaseTests {

  @Autowired
  private EntityManager entityManager;

  @LocalServerPort
  private int port;

  @Test
  @Transactional
  void savesAndReloadsEveryContentSubtypeAndOptionalCompletion() {
    Course course = new Course("DAT250", "Database test", Term.AUTUMN, 2026);
    entityManager.persist(course);
    Module module = new Module(course, "Week 1");
    entityManager.persist(module);
    User user = new User("h2-test@example.com", "Student");
    entityManager.persist(user);

    List<Content> items = List.of(
        new StaticPage(module, "Read", 0, "Welcome"),
        new Quiz(module, "Quiz", 1, 3),
        new Assignment(module, "Assignment", 2, "Upload a document"),
        new CodingExercise(module, "Code", 3, "https://example.com/repository.git"));
    items.forEach(entityManager::persist);

    StudentInteraction pending = new StudentInteraction(user, items.get(0));
    StudentInteraction completed = new StudentInteraction(user, items.get(1));
    completed.markCompleted();
    entityManager.persist(pending);
    entityManager.persist(completed);
    entityManager.flush();
    entityManager.clear();

    assertEquals("Welcome", ((StaticPage) entityManager.find(Content.class, items.get(0).getId())).getBody());
    assertEquals(3, ((Quiz) entityManager.find(Content.class, items.get(1).getId())).getMaxAttempts());
    assertEquals("Upload a document", ((Assignment) entityManager.find(Content.class, items.get(2).getId())).getInstructions());
    assertEquals("https://example.com/repository.git",
        ((CodingExercise) entityManager.find(Content.class, items.get(3).getId())).getRepositoryUrl());

    StudentInteraction loaded = entityManager.find(StudentInteraction.class, pending.getId());
    assertNull(loaded.getCompletedAt());
    assertEquals(user.getEmail(), loaded.getUser().getEmail());
    assertEquals(items.get(0).getId(), loaded.getContent().getId());
    assertNotNull(entityManager.find(StudentInteraction.class, completed.getId()).getCompletedAt());
  }

  @Test
  @Transactional
  void cascadesQuizQuestionsAndAnswerOptionsThroughSaveAndReload() {
    Course course = new Course("DAT250", "Database test", Term.AUTUMN, 2026);
    entityManager.persist(course);
    Module module = new Module(course, "Week 1");
    entityManager.persist(module);

    Quiz quiz = new Quiz(module, "Week 1 Quiz", 0, 3);
    Question question = quiz.addQuestion("Which are prime?");
    question.addOption("2", true);
    question.addOption("3", true);
    question.addOption("4", false);
    entityManager.persist(quiz);
    entityManager.flush();
    entityManager.clear();

    Quiz reloaded = entityManager.find(Quiz.class, quiz.getId());
    assertEquals(1, reloaded.getQuestions().size());
    Question reloadedQuestion = reloaded.getQuestions().get(0);
    assertEquals("Which are prime?", reloadedQuestion.getText());
    assertEquals(3, reloadedQuestion.getOptions().size());
    long correctCount = reloadedQuestion.getOptions().stream().filter(AnswerOption::isCorrect).count();
    assertEquals(2, correctCount);
  }

  @Test
  void consoleIsAccessibleButApiStillRequiresAuthentication() throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> console = client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, console.statusCode());
      assertTrue(console.body().contains("H2 Console"));
      assertEquals("SAMEORIGIN", console.headers().firstValue("X-Frame-Options").orElseThrow());

      HttpResponse<String> api = client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/courses"))
              .header("Accept", "application/json").GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(401, api.statusCode());
    }
  }
}
