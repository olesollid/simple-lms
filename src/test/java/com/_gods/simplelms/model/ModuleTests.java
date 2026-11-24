package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Term;

class ModuleTests {

  private Course course() {
    return new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
  }

  @Test
  void createsModuleWithValidFields() {
    Course course = course();
    Module module = new Module(course, "Week 1: Introduction");

    assertEquals(course, module.getCourse());
    assertEquals("Week 1: Introduction", module.getTitle());
    assertTrue(module.getDependencies().isEmpty());
  }

  @Test
  void rejectsNullCourse() {
    assertThrows(IllegalArgumentException.class, () -> new Module(null, "Week 1"));
  }

  @Test
  void rejectsBlankTitle() {
    assertThrows(IllegalArgumentException.class, () -> new Module(course(), "  "));
  }

  @Test
  void addsDependencyWithinSameCourse() {
    Course course = course();
    Module week1 = new Module(course, "Week 1");
    Module week2 = new Module(course, "Week 2");

    week2.addDependency(week1);

    assertTrue(week2.getDependencies().contains(week1));
  }

  @Test
  void rejectsDependencyFromDifferentCourse() {
    Module week1 = new Module(course(), "Week 1");
    Module week2 = new Module(course(), "Week 2");

    assertThrows(IllegalArgumentException.class, () -> week2.addDependency(week1));
  }

  @Test
  void rejectsSelfDependency() {
    Course course = course();
    Module module = new Module(course, "Week 1");

    assertThrows(IllegalArgumentException.class, () -> module.addDependency(module));
  }

  @Test
  void rejectsDependencyThatWouldCreateCycle() {
    Course course = course();
    Module a = new Module(course, "A");
    Module b = new Module(course, "B");
    Module c = new Module(course, "C");
    b.addDependency(a);
    c.addDependency(b);

    assertThrows(IllegalArgumentException.class, () -> a.addDependency(c));
  }

  @Test
  void removesDependency() {
    Course course = course();
    Module week1 = new Module(course, "Week 1");
    Module week2 = new Module(course, "Week 2");
    week2.addDependency(week1);

    week2.removeDependency(week1);

    assertFalse(week2.getDependencies().contains(week1));
  }
}
