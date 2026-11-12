package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.ModuleRepository;

@DataJpaTest
@Import(ContentService.class)
class ContentServiceTests {

  @Autowired
  private ContentService contentService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  private Module module() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    return moduleRepository.save(new Module(course, "Week 1"));
  }

  @Test
  void createsStaticPage() {
    Module module = module();

    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null);

    assertEquals("Syllabus", page.getTitle());
    assertEquals("Welcome!", page.getBody());
    assertEquals(module.getId(), page.getModule().getId());
  }

  @Test
  void throwsWhenCreatingStaticPageForMissingModule() {
    assertThrows(ModuleNotFoundException.class,
        () -> contentService.createStaticPage(999L, "Syllabus", 0, "Welcome!", null, null));
  }

  @Test
  void retrievesContentsByModuleInPositionOrder() {
    Module module = module();
    contentService.createStaticPage(module.getId(), "Second", 1, "B", null, null);
    contentService.createStaticPage(module.getId(), "First", 0, "A", null, null);

    List<Content> contents = contentService.getContentsByModule(module.getId());

    assertEquals(List.of("First", "Second"), contents.stream().map(Content::getTitle).toList());
  }

  @Test
  void throwsWhenRetrievingContentsForMissingModule() {
    assertThrows(ModuleNotFoundException.class, () -> contentService.getContentsByModule(999L));
  }

  @Test
  void throwsWhenContentNotFound() {
    assertThrows(ContentNotFoundException.class, () -> contentService.getContentById(999L));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.updateContentMetadata(999L, "x", 0, null, null));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.updateStaticPageBody(999L, "x"));
    assertThrows(ContentNotFoundException.class, () -> contentService.deleteContent(999L));
  }

  @Test
  void updatesContentMetadata() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null);
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = LocalDateTime.of(2026, 1, 8, 8, 0);

    Content updated = contentService.updateContentMetadata(page.getId(), "Updated", 3, from, until);

    assertEquals("Updated", updated.getTitle());
    assertEquals(3, updated.getPosition());
    assertEquals(from, updated.getVisibleFrom());
    assertEquals(until, updated.getVisibleUntil());
  }

  @Test
  void rejectsInvalidVisibilityWindowOnUpdate() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null);
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 8, 0);
    LocalDateTime until = from.minusMinutes(1);

    assertThrows(IllegalArgumentException.class,
        () -> contentService.updateContentMetadata(page.getId(), "Syllabus", 0, from, until));
  }

  @Test
  void updatesStaticPageBody() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null);

    StaticPage updated = contentService.updateStaticPageBody(page.getId(), "Updated body");

    assertEquals("Updated body", updated.getBody());
  }

  @Test
  void deletesContent() {
    Module module = module();
    StaticPage page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "Welcome!",
        null, null);

    contentService.deleteContent(page.getId());

    assertTrue(contentService.getContentsByModule(module.getId()).isEmpty());
    assertThrows(ContentNotFoundException.class, () -> contentService.getContentById(page.getId()));
  }
}
