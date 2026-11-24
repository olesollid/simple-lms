package com._gods.simplelms;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.Submission;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;

class ProdPersistenceTests {
  @TempDir
  Path directory;

  private ConfigurableApplicationContext start() {
    String database = directory.resolve("simplelms").toAbsolutePath().toString().replace('\\', '/');
    return new SpringApplicationBuilder(SimpleLMSApplication.class).profiles("prod").run(
        "--spring.datasource.url=jdbc:h2:file:" + database + ";DB_CLOSE_ON_EXIT=FALSE",
        "--server.port=0",
        // Verify prod persistence without requiring a broker or processing real events.
        "--spring.rabbitmq.listener.simple.auto-startup=false",
        "--spring.rabbitmq.listener.direct.auto-startup=false",
        "--spring.main.banner-mode=off",
        "--logging.level.root=WARN",
        "--logging.level.org.hibernate.SQL=OFF");
  }

  @Test
  void prodRetainsDomainDataAndQuizIdentityAcrossApplicationRestarts() {
    Long courseId;
    Long moduleId;
    Long assignmentId;
    Long submissionId;
    Long quizId;
    try (var context = start()) {
      assertEquals("update", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
      assertEquals("false", context.getEnvironment().getProperty("spring.h2.console.enabled"));
      assertFalse(context.containsBean("h2Console"));
      try (EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
        em.getTransaction().begin();
        User user = new User("persistent@example.com", "Persistent student");
        Course course = new Course("DAT250", "Persistent course", Term.AUTUMN, 2026);
        em.persist(user);
        em.persist(course);
        em.persist(new Enrollment(user, course, Role.STUDENT));
        Module module = new Module(course, "Week 1");
        em.persist(module);
        Assignment assignment = new Assignment(module, "Essay", 0, "Write an essay");
        em.persist(assignment);
        Submission submission = new Submission(user, assignment, "My answer");
        submission.grade(85, "Well done");
        em.persist(submission);
        Quiz quiz = new Quiz(module, "Original quiz", 1, 1);
        em.persist(quiz);
        em.getTransaction().commit();
        courseId = course.getId();
        moduleId = module.getId();
        assignmentId = assignment.getId();
        submissionId = submission.getId();
        quizId = quiz.getId();
      }
    }

    // A new application context, connection pool and Hibernate factory open the same file.
    try (var context = start();
        EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
      em.getTransaction().begin();
      assertEquals("DAT250", em.find(Course.class, courseId).getTitle());
      assertEquals("Week 1", em.find(Module.class, moduleId).getTitle());
      assertEquals("Write an essay", em.find(Assignment.class, assignmentId).getInstructions());
      Submission saved = em.find(Submission.class, submissionId);
      assertEquals("My answer", saved.getText());
      assertEquals(85.0, saved.getGrade());
      assertEquals("Well done", saved.getFeedback());
      assertEquals("persistent@example.com", saved.getSubmitter().getEmail());
      assertEquals(1L, em.createQuery("select count(e) from Enrollment e", Long.class).getSingleResult());
      assertEquals("Original quiz", em.find(Quiz.class, quizId).getTitle());
      Quiz newQuiz = new Quiz(em.find(Module.class, moduleId), "New quiz", 2, 1);
      em.persist(newQuiz);
      em.flush();
      assertTrue(newQuiz.getId() > quizId, "A new quiz must not reuse a previous quiz's Redis key");
      em.getTransaction().commit();
    }
  }
}
