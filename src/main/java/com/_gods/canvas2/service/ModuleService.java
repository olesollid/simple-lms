package com._gods.canvas2.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;
import com._gods.canvas2.repository.SubmissionRepository;

@Service
@Transactional
public class ModuleService {

  private final ModuleRepository moduleRepository;
  private final CourseRepository courseRepository;
  private final EnrollmentService enrollmentService;
  private final StudentInteractionService studentInteractionService;
  private final ContentRepository contentRepository;
  private final SubmissionRepository submissionRepository;
  private final StudentInteractionRepository studentInteractionRepository;

  public ModuleService(ModuleRepository moduleRepository, CourseRepository courseRepository,
      EnrollmentService enrollmentService, StudentInteractionService studentInteractionService,
      ContentRepository contentRepository, SubmissionRepository submissionRepository,
      StudentInteractionRepository studentInteractionRepository) {
    this.moduleRepository = moduleRepository;
    this.courseRepository = courseRepository;
    this.enrollmentService = enrollmentService;
    this.studentInteractionService = studentInteractionService;
    this.contentRepository = contentRepository;
    this.submissionRepository = submissionRepository;
    this.studentInteractionRepository = studentInteractionRepository;
  }

  public Module createModule(Long courseId, String title, String requesterEmail) {
    requireTeacher(courseId, requesterEmail);
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    Module module = new Module(course, title);
    // New modules go at the end of the course.
    module.moveTo(moduleRepository.findByCourse(course).stream()
        .mapToInt(existing -> existing.getPosition() + 1).max().orElse(0));
    return moduleRepository.save(module);
  }

  /**
   * Teacher-only: puts the course's modules in the given order. The list must contain each of the
   * course's modules exactly once, and a module can never come before one of its prerequisites --
   * students work through modules top to bottom, so that order is rejected with a reason.
   */
  public List<Module> reorderModules(Long courseId, List<Long> moduleIds, String requesterEmail) {
    requireTeacher(courseId, requesterEmail);
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    List<Module> current = moduleRepository.findByCourse(course);
    requireSameIds(current.stream().map(Module::getId).toList(), moduleIds, "modules");
    Map<Long, Module> byId = new HashMap<>();
    current.forEach(module -> byId.put(module.getId(), module));
    for (Module module : current) {
      for (Module prerequisite : module.getDependencies()) {
        if (moduleIds.indexOf(prerequisite.getId()) > moduleIds.indexOf(module.getId())) {
          throw new IllegalArgumentException(module.getTitle() + " must stay below "
              + prerequisite.getTitle() + ", because " + prerequisite.getTitle()
              + " is a prerequisite of " + module.getTitle() + ".");
        }
      }
    }
    for (int i = 0; i < moduleIds.size(); i++) {
      byId.get(moduleIds.get(i)).moveTo(i);
    }
    moduleRepository.saveAll(current);
    return topologicalSort(moduleRepository.findByCourseOrderByPositionAscIdAsc(course));
  }

  /** The new order must name every existing item exactly once, and nothing else. */
  static void requireSameIds(List<Long> existing, List<Long> requested, String what) {
    if (requested == null || requested.size() != existing.size()
        || !new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(existing))) {
      throw new IllegalArgumentException("The new order must list each of the " + what + " exactly once");
    }
  }

  @Transactional(readOnly = true)
  public List<Module> getModulesByCourse(Long courseId, String requesterEmail) {
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    return topologicalSort(moduleRepository.findByCourseOrderByPositionAscIdAsc(course));
  }

  @Transactional(readOnly = true)
  public Module getModuleById(Long id, String requesterEmail) {
    Module module = getModuleById(id);
    enrollmentService.requireEnrolled(module.getCourse().getId(), requesterEmail);
    return module;
  }

  private Module getModuleById(Long id) {
    Module module = moduleRepository.findById(id)
        .orElseThrow(() -> new ModuleNotFoundException(id));
    // Force the lazy dependency collection to load before the transaction (and its session)
    // closes, since the entity is returned to callers outside this method's transaction.
    module.initializeDependencies();
    return module;
  }

  public Module updateModuleTitle(Long id, String title, String requesterEmail) {
    Module module = getModuleById(id);
    requireTeacher(module.getCourse().getId(), requesterEmail);
    module.setTitle(title);
    return moduleRepository.save(module);
  }

  /**
   * Deletes a module, removing it from any other module's dependency list first.
   * Also deletes the module's own content, and each content's submissions and student
   * interactions, which would otherwise block the delete with a foreign-key violation.
   * Returns the modules that had the dependency removed, so callers can surface
   * what changed.
   */
  public List<Module> deleteModule(Long id, String requesterEmail) {
    Module module = getModuleById(id);
    requireTeacher(module.getCourse().getId(), requesterEmail);
    List<Module> affected = moduleRepository.findByCourse(module.getCourse()).stream()
        .filter(other -> other.getDependencies().contains(module))
        .toList();
    affected.forEach(other -> other.removeDependency(module));
    moduleRepository.saveAll(affected);
    for (Content content : contentRepository.findByModuleOrderByPosition(module)) {
      submissionRepository.deleteAll(submissionRepository.findByContent(content));
      studentInteractionRepository.deleteAll(studentInteractionRepository.findByContent(content));
      contentRepository.delete(content);
    }
    moduleRepository.delete(module);
    return affected;
  }

  public Module addDependency(Long moduleId, Long dependsOnModuleId, String requesterEmail) {
    Module module = getModuleById(moduleId);
    requireTeacher(module.getCourse().getId(), requesterEmail);
    Module dependsOn = getModuleById(dependsOnModuleId);
    module.addDependency(dependsOn);
    return moduleRepository.save(module);
  }

  public Module removeDependency(Long moduleId, Long dependsOnModuleId, String requesterEmail) {
    Module module = getModuleById(moduleId);
    requireTeacher(module.getCourse().getId(), requesterEmail);
    Module dependsOn = getModuleById(dependsOnModuleId);
    module.removeDependency(dependsOn);
    return moduleRepository.save(module);
  }

  /**
   * A module is locked for a student if they haven't yet completed every content item in all of
   * its dependency modules. Teachers are never locked out of any module, since they administer
   * the course rather than progress through it.
   */
  @Transactional(readOnly = true)
  public boolean isLocked(Module module, String requesterEmail) {
    if (enrollmentService.hasRole(module.getCourse().getId(), requesterEmail, Role.TEACHER)) {
      return false;
    }
    return !studentInteractionService.hasCompletedDependencies(module, requesterEmail);
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }

  /**
   * Orders modules so every module appears after its dependencies (Kahn's algorithm),
   * breaking ties by id so the order is deterministic across calls.
   */
  private static final Comparator<Module> TEACHER_ORDER =
      Comparator.comparingInt(Module::getPosition).thenComparing(Module::getId);

  private List<Module> topologicalSort(List<Module> modules) {
    Map<Long, Integer> remainingDependencies = new HashMap<>();
    Map<Long, List<Module>> dependents = new HashMap<>();

    for (Module module : modules) {
      remainingDependencies.put(module.getId(), module.getDependencies().size());
      for (Module dependency : module.getDependencies()) {
        dependents.computeIfAbsent(dependency.getId(), key -> new ArrayList<>()).add(module);
      }
    }

    Queue<Module> ready = new ArrayDeque<>();
    modules.stream()
        .filter(module -> remainingDependencies.get(module.getId()) == 0)
        .sorted(TEACHER_ORDER)
        .forEach(ready::add);

    List<Module> ordered = new ArrayList<>();
    while (!ready.isEmpty()) {
      List<Module> batch = new ArrayList<>(ready);
      ready.clear();
      batch.sort(TEACHER_ORDER);

      for (Module module : batch) {
        ordered.add(module);
        for (Module dependent : dependents.getOrDefault(module.getId(), List.of())) {
          int remaining = remainingDependencies.merge(dependent.getId(), -1, Integer::sum);
          if (remaining == 0) {
            ready.add(dependent);
          }
        }
      }
    }

    return ordered;
  }
}
