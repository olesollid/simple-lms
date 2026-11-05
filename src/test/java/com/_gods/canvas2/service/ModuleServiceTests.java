package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.repository.CourseRepository;

@DataJpaTest
@Import(ModuleService.class)
class ModuleServiceTests {

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private CourseRepository courseRepository;

  private Course course() {
    return courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
  }

  @Test
  void createsModuleWithinCourse() {
    Course course = course();

    Module module = moduleService.createModule(course.getId(), "Week 1");

    assertEquals("Week 1", module.getTitle());
    assertEquals(course.getId(), module.getCourse().getId());
  }

  @Test
  void throwsWhenCreatingModuleForMissingCourse() {
    assertThrows(CourseNotFoundException.class, () -> moduleService.createModule(999L, "Week 1"));
  }

  @Test
  void retrievesModulesByCourseInTopologicalOrder() {
    Course course = course();
    Module a = moduleService.createModule(course.getId(), "A");
    Module b = moduleService.createModule(course.getId(), "B");
    Module c = moduleService.createModule(course.getId(), "C");
    moduleService.addDependency(b.getId(), a.getId());
    moduleService.addDependency(c.getId(), b.getId());

    List<Module> ordered = moduleService.getModulesByCourse(course.getId());

    assertEquals(List.of(a.getId(), b.getId(), c.getId()),
        ordered.stream().map(Module::getId).toList());
  }

  @Test
  void retrievesModulesWithNoDependenciesInDeterministicOrder() {
    Course course = course();
    Module first = moduleService.createModule(course.getId(), "First");
    Module second = moduleService.createModule(course.getId(), "Second");

    List<Module> firstCall = moduleService.getModulesByCourse(course.getId());
    List<Module> secondCall = moduleService.getModulesByCourse(course.getId());

    assertEquals(List.of(first.getId(), second.getId()),
        firstCall.stream().map(Module::getId).toList());
    assertEquals(firstCall.stream().map(Module::getId).toList(),
        secondCall.stream().map(Module::getId).toList());
  }

  @Test
  void updatesModuleTitle() {
    Course course = course();
    Module module = moduleService.createModule(course.getId(), "Week 1");

    Module updated = moduleService.updateModuleTitle(module.getId(), "Week 1 - Updated");

    assertEquals("Week 1 - Updated", updated.getTitle());
  }

  @Test
  void throwsWhenModuleNotFound() {
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(999L));
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.updateModuleTitle(999L, "x"));
    assertThrows(ModuleNotFoundException.class, () -> moduleService.deleteModule(999L));
  }

  @Test
  void deletesModuleWithNoDependents() {
    Course course = course();
    Module module = moduleService.createModule(course.getId(), "Week 1");

    List<Module> affected = moduleService.deleteModule(module.getId());

    assertTrue(affected.isEmpty());
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(module.getId()));
  }

  @Test
  void deletingModuleWithDependentsCascadesAndReturnsAffectedModules() {
    Course course = course();
    Module a = moduleService.createModule(course.getId(), "A");
    Module b = moduleService.createModule(course.getId(), "B");
    Module c = moduleService.createModule(course.getId(), "C");
    moduleService.addDependency(b.getId(), a.getId());
    moduleService.addDependency(c.getId(), a.getId());

    List<Module> affected = moduleService.deleteModule(a.getId());

    assertEquals(List.of(b.getId(), c.getId()),
        affected.stream().map(Module::getId).sorted().toList());
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(a.getId()));
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().isEmpty());
    assertTrue(moduleService.getModuleById(c.getId()).getDependencies().isEmpty());
  }

  @Test
  void deletingModuleDoesNotAffectModulesInOtherCourses() {
    Course course = course();
    Course otherCourse = course();
    Module a = moduleService.createModule(course.getId(), "A");
    Module unrelated = moduleService.createModule(otherCourse.getId(), "Unrelated");

    List<Module> affected = moduleService.deleteModule(a.getId());

    assertTrue(affected.isEmpty());
    assertTrue(moduleService.getModuleById(unrelated.getId()).getDependencies().isEmpty());
  }

  @Test
  void addsAndRemovesDependency() {
    Course course = course();
    Module a = moduleService.createModule(course.getId(), "A");
    Module b = moduleService.createModule(course.getId(), "B");

    moduleService.addDependency(b.getId(), a.getId());
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().contains(a));

    moduleService.removeDependency(b.getId(), a.getId());
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().isEmpty());
  }

  @Test
  void rejectsAddingSelfOrCyclicDependency() {
    Course course = course();
    Module a = moduleService.createModule(course.getId(), "A");
    Module b = moduleService.createModule(course.getId(), "B");
    moduleService.addDependency(b.getId(), a.getId());

    assertThrows(IllegalArgumentException.class,
        () -> moduleService.addDependency(a.getId(), a.getId()));
    assertThrows(IllegalArgumentException.class,
        () -> moduleService.addDependency(a.getId(), b.getId()));
  }
}
