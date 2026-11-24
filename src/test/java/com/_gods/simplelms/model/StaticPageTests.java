package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.StaticPage;
import com._gods.simplelms.model.Term;

class StaticPageTests {

  private Module module() {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    return new Module(course, "Week 1: Introduction");
  }

  @Test
  void createsPageWithBody() {
    StaticPage page = new StaticPage(module(), "Syllabus", 0, "Welcome to the course!");

    assertEquals("Welcome to the course!", page.getBody());
  }

  @Test
  void rejectsNullBody() {
    assertThrows(IllegalArgumentException.class, () -> new StaticPage(module(), "Syllabus", 0, null));
  }

  @Test
  void setBodyUpdatesBody() {
    StaticPage page = new StaticPage(module(), "Syllabus", 0, "Welcome to the course!");

    page.setBody("Updated welcome message");

    assertEquals("Updated welcome message", page.getBody());
  }

  @Test
  void setBodyRejectsNull() {
    StaticPage page = new StaticPage(module(), "Syllabus", 0, "Welcome to the course!");

    assertThrows(IllegalArgumentException.class, () -> page.setBody(null));
  }
}
