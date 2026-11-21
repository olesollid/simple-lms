package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;
import com._gods.canvas2.repository.SubmissionRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({ModuleService.class, ContentService.class, EnrollmentService.class, UserService.class,
    StudentInteractionService.class})
class ModuleServiceTests {

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private ContentService contentService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private SubmissionRepository submissionRepository;

  @Autowired
  private StudentInteractionRepository studentInteractionRepository;

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
    return studentUserOf(course).getEmail();
  }

  private User studentUserOf(Course course) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));
    return user;
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

    List<Module> ordered = moduleService.getModulesByCourse(course.getId(), teacher);

    assertEquals(List.of(a.getId(), b.getId(), c.getId()),
        ordered.stream().map(Module::getId).toList());
  }

  @Test
  void rejectsGetModulesByCourseWhenNotEnrolled() {
    Course course = course();
    teacherOf(course);

    assertThrows(ForbiddenException.class,
        () -> moduleService.getModulesByCourse(course.getId(), "stranger@example.com"));
  }

  @Test
  void studentCanListModulesByCourse() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    List<Module> modules = moduleService.getModulesByCourse(course.getId(), student);

    assertEquals(List.of(module.getId()), modules.stream().map(Module::getId).toList());
  }

  @Test
  void retrievesModulesWithNoDependenciesInDeterministicOrder() {
    Course course = course();
    String teacher = teacherOf(course);
    Module first = moduleService.createModule(course.getId(), "First", teacher);
    Module second = moduleService.createModule(course.getId(), "Second", teacher);

    List<Module> firstCall = moduleService.getModulesByCourse(course.getId(), teacher);
    List<Module> secondCall = moduleService.getModulesByCourse(course.getId(), teacher);

    assertEquals(List.of(first.getId(), second.getId()),
        firstCall.stream().map(Module::getId).toList());
    assertEquals(firstCall.stream().map(Module::getId).toList(),
        secondCall.stream().map(Module::getId).toList());
  }

  @Test
  void teacherReordersModulesAroundTheirPrerequisites() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);
    Module c = moduleService.createModule(course.getId(), "C", teacher);
    moduleService.addDependency(b.getId(), a.getId(), teacher);

    moduleService.reorderModules(course.getId(), List.of(c.getId(), a.getId(), b.getId()), teacher);

    assertEquals(List.of("C", "A", "B"), moduleService.getModulesByCourse(course.getId(), teacher)
        .stream().map(Module::getTitle).toList());
  }

  @Test
  void refusesToMoveAModuleAboveOneOfItsPrerequisitesAndSaysWhy() {
    Course course = course();
    String teacher = teacherOf(course);
    Module week1 = moduleService.createModule(course.getId(), "Week 1", teacher);
    Module week2 = moduleService.createModule(course.getId(), "Week 2", teacher);
    Module week3 = moduleService.createModule(course.getId(), "Week 3", teacher);
    moduleService.addDependency(week3.getId(), week1.getId(), teacher);
    moduleService.addDependency(week3.getId(), week2.getId(), teacher);

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> moduleService.reorderModules(course.getId(),
            List.of(week1.getId(), week3.getId(), week2.getId()), teacher));

    assertEquals("Week 3 must stay below Week 2, because Week 2 is a prerequisite of Week 3.",
        refused.getMessage());
    assertEquals(List.of("Week 1", "Week 2", "Week 3"),
        moduleService.getModulesByCourse(course.getId(), teacher).stream().map(Module::getTitle).toList());
  }

  @Test
  void newModulesGoAtTheEndOfTheCourse() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);
    moduleService.reorderModules(course.getId(), List.of(b.getId(), a.getId()), teacher);

    Module c = moduleService.createModule(course.getId(), "C", teacher);

    assertEquals(2, c.getPosition());
    assertEquals(List.of("B", "A", "C"), moduleService.getModulesByCourse(course.getId(), teacher)
        .stream().map(Module::getTitle).toList());
  }

  @Test
  void rejectsReorderingModulesByStudentsOrWithMissingModules() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    moduleService.createModule(course.getId(), "B", teacher);
    String student = studentOf(course);

    assertThrows(IllegalArgumentException.class,
        () -> moduleService.reorderModules(course.getId(), List.of(a.getId()), teacher));
    assertThrows(ForbiddenException.class,
        () -> moduleService.reorderModules(course.getId(), List.of(a.getId()), student));
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
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.getModuleById(999L, "nobody@example.com"));
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.updateModuleTitle(999L, "x", "nobody@example.com"));
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.deleteModule(999L, "nobody@example.com"));
  }

  @Test
  void rejectsGetModuleByIdWhenNotEnrolled() {
    Course course = course();
    String teacher = teacherOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertThrows(ForbiddenException.class,
        () -> moduleService.getModuleById(module.getId(), "stranger@example.com"));
  }

  @Test
  void studentCanGetModuleById() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    assertEquals("Week 1", moduleService.getModuleById(module.getId(), student).getTitle());
  }

  @Test
  void deletesModuleWithNoDependents() {
    Course course = course();
    String teacher = teacherOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);

    List<Module> affected = moduleService.deleteModule(module.getId(), teacher);

    assertTrue(affected.isEmpty());
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.getModuleById(module.getId(), teacher));
  }

  @Test
  void deletingModuleAlsoDeletesItsContentAndTheirSubmissionsAndInteractions() {
    Course course = course();
    String teacher = teacherOf(course);
    Module module = moduleService.createModule(course.getId(), "Week 1", teacher);
    User student = studentUserOf(course);
    Assignment assignment = contentService.createAssignment(module.getId(), "Essay", 0,
        "Write something", null, false, false, teacher);
    submissionRepository.save(new Submission(student, assignment, "My essay"));
    studentInteractionRepository.save(new StudentInteraction(student, assignment));

    moduleService.deleteModule(module.getId(), teacher);

    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.getModuleById(module.getId(), teacher));
    assertThrows(ContentNotFoundException.class,
        () -> contentService.getContentById(assignment.getId(), teacher));
    assertTrue(submissionRepository.findByContent(assignment).isEmpty());
    assertTrue(studentInteractionRepository.findByContent(assignment).isEmpty());
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
    assertThrows(ModuleNotFoundException.class,
        () -> moduleService.getModuleById(a.getId(), teacher));
    assertTrue(moduleService.getModuleById(b.getId(), teacher).getDependencies().isEmpty());
    assertTrue(moduleService.getModuleById(c.getId(), teacher).getDependencies().isEmpty());
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
    assertTrue(
        moduleService.getModuleById(unrelated.getId(), otherTeacher).getDependencies().isEmpty());
  }

  @Test
  void addsAndRemovesDependency() {
    Course course = course();
    String teacher = teacherOf(course);
    Module a = moduleService.createModule(course.getId(), "A", teacher);
    Module b = moduleService.createModule(course.getId(), "B", teacher);

    moduleService.addDependency(b.getId(), a.getId(), teacher);
    assertTrue(moduleService.getModuleById(b.getId(), teacher).getDependencies().contains(a));

    moduleService.removeDependency(b.getId(), a.getId(), teacher);
    assertTrue(moduleService.getModuleById(b.getId(), teacher).getDependencies().isEmpty());
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
