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
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Submission;
import com._gods.simplelms.model.SubmissionFile;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;

class SubmissionFilePersistenceTests {
  @TempDir
  Path directory;

  private ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(SimpleLMSApplication.class).run(
        "--spring.datasource.url=jdbc:h2:file:"
            + directory.resolve("uploads").toAbsolutePath().toString().replace('\\', '/')
            + ";DB_CLOSE_ON_EXIT=FALSE",
        "--spring.jpa.hibernate.ddl-auto=update",
        "--spring.docker.compose.enabled=false", "--server.port=0",
        "--spring.main.banner-mode=off");
  }

  @Test
  void documentBytesSurviveClosingAndReopeningTheApplication() {
    byte[] document = "%PDF-1.4 persistent attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Long id;
    try (var context = start();
        EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
      em.getTransaction().begin();
      User student = new User("file@example.com", "Student");
      Course course = new Course("Files", "Persistence", Term.AUTUMN, 2026);
      em.persist(student);
      em.persist(course);
      Module module = new Module(course, "Week 1");
      em.persist(module);
      Assignment assignment = new Assignment(module, "Report", 0, "Upload a document");
      em.persist(assignment);
      Submission submission = new Submission(student, assignment, "", new SubmissionFile("report.pdf", document));
      em.persist(submission);
      em.getTransaction().commit();
      id = submission.getId();
    }
    try (var context = start();
        EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
      SubmissionFile file = em.find(Submission.class, id).getFile();
      assertEquals("report.pdf", file.getFilename());
      assertEquals("application/pdf", file.getContentType());
      assertEquals(document.length, file.getSize());
      assertArrayEquals(document, file.getData());
    }
  }
}
