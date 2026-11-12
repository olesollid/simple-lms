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
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({ModuleService.class, EnrollmentService.class, UserService.class})
class ModuleServiceTests {

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  private static int userCounter = 0;

  private Course course() {
    return courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
  }

  private String teacherOf(Course course) {
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    return email;
  }

  private String studentOf(Course course) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));
    return email;
  }

  @Test
  void createsModuleWithinCourse() {
    Course course = course();
    String teacher = teacherOf(course);

    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertEquals("Week 1", module.getTitle());
    assertEquals(course.getId(), module.getCourse().getId());
  }

  @Test
  void throwsWhenCreatingModuleForMissingCourse() {
    assertThrows(CourseNotFoundException.class,
        () -> moduleService.createModule(999L, "Week 1", "nobody@example.com"));
  }

  @Test
  void rejectsCreatingModuleByStudent() {
    Course course = course();
    String student = studentOf(course);

    assertThrows(ForbiddenException.class,
        () -> moduleService.createModule(course.getId(), "Week 1", student));
  }

  @Test
  void rejectsCreatingModuleByTeacherOfAnotherCourse() {
    Course course = course();
    Course otherCourse = course();
    String teacherElsewhere = teacherOf(otherCourse);

    assertThrows(ForbiddenException.class,
        () -> moduleService.createModule(course.getId(), "Week 1", teacherElsewhere));
  }

  @Test
  void retrievesModulesByCourseInTopologicalOrder() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);
    Module c = moduleService.createModule(course.getId(), "C", teacher);
    moduleService.addDependency(b.getId(), a.getId(), teacher);
    moduleService.addDependency(c.getId(), b.getId(), teacher);

    List<Module> ordered = moduleService.getModulesByCourse(course.getId());

    assertEquals(List.of(a.getId(), b.getId(), c.getId()),
        ordered.stream().map(Module::getId).toList());
  }

  @Test
  void retrievesModulesWithNoDependenciesInDeterministicOrder() {
    Course course = course();
    String teacher = teacherOf(course);
    Module first = moduleService.createModule(course.getId(), "First", teacher);
    Module second = moduleService.createModule(course.getId(), "Second", teacher);

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
    String teacher = teacherOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    Module updated = moduleService.updateModuleTitle(module.getId(), "Week 1 - Updated", teacher);

    assertEquals("Week 1 - Updated", updated.getTitle());
  }

  @Test
  void rejectsUpdatingModuleTitleByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertThrows(ForbiddenException.class,
        () -> moduleService.updateModuleTitle(module.getId(), "Hacked", student));
  }

  @Test
  void throwsWhenModuleNotFound() {
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(999L));
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.updateModuleTitle(999L, "x", "nobody@example.com"));
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.deleteModule(999L, "nobody@example.com"));
  }

  @Test
  void deletesModuleWithNoDependents() {
    Course course = course();
    String teacher = teacherOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    List<Module> affected = moduleService.deleteModule(module.getId(), teacher);

    assertTrue(affected.isEmpty());
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(module.getId()));
  }

  @Test
  void rejectsDeletingModuleByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertThrows(ForbiddenException.class,
        () -> moduleService.deleteModule(module.getId(), student));
  }

  @Test
  void deletingModuleWithDependentsCascadesAndReturnsAffectedModules() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);
    Module c = moduleService.createModule(course.getId(), "C", teacher);
    moduleService.addDependency(b.getId(), a.getId(), teacher);
    moduleService.addDependency(c.getId(), a.getId(), teacher);

    List<Module> affected = moduleService.deleteModule(a.getId(), teacher);

    assertEquals(List.of(b.getId(), c.getId()),
        affected.stream().map(Module::getId).sorted().toList());
    assertThrows(ModuleNotFoundException.class, () -> moduleService.getModuleById(a.getId()));
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().isEmpty());
    assertTrue(moduleService.getModuleById(c.getId()).getDependencies().isEmpty());
  }

  @Test
  void deletingModuleDoesNotAffectModulesInOtherCourses() {
    Course course = course();
    String teacher = teacherOf(course);
    Course otherCourse = course();
    String otherTeacher = teacherOf(otherCourse);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module unrelated = moduleService.createModule(otherCourse.getId(), "Unrelated", otherTeacher);

    List<Module> affected = moduleService.deleteModule(a.getId(), teacher);

    assertTrue(affected.isEmpty());
    assertTrue(moduleService.getModuleById(unrelated.getId()).getDependencies().isEmpty());
  }

  @Test
  void addsAndRemovesDependency() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);

    moduleService.addDependency(b.getId(), a.getId(), teacher);
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().contains(a));

    moduleService.removeDependency(b.getId(), a.getId(), teacher);
    assertTrue(moduleService.getModuleById(b.getId()).getDependencies().isEmpty());
  }

  @Test
  void rejectsAddingDependencyByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);

    assertThrows(ForbiddenException.class,
        () -> moduleService.addDependency(b.getId(), a.getId(), student));
  }

  @Test
  void rejectsAddingSelfOrCyclicDependency() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);
    moduleService.addDependency(b.getId(), a.getId(), teacher);

    assertThrows(IllegalArgumentException.class,
        () -> moduleService.addDependency(a.getId(), a.getId(), teacher));
    assertThrows(IllegalArgumentException.class,
        () -> moduleService.addDependency(a.getId(), b.getId(), teacher));
  }
}
