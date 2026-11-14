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

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
@Transactional
public class ModuleService {

  private final ModuleRepository moduleRepository;
  private final CourseRepository courseRepository;
  private final EnrollmentService enrollmentService;

  public ModuleService(ModuleRepository moduleRepository, CourseRepository courseRepository,
      EnrollmentService enrollmentService) {
    this.moduleRepository = moduleRepository;
    this.courseRepository = courseRepository;
    this.enrollmentService = enrollmentService;
  }

  public Module createModule(Long courseId, String title, String requesterEmail) {
    requireTeacher(courseId, requesterEmail);
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    return moduleRepository.save(new Module(course, title));
  }

  @Transactional(readOnly = true)
  public List<Module> getModulesByCourse(Long courseId, String requesterEmail) {
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    return topologicalSort(moduleRepository.findByCourse(course));
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
        .sorted(Comparator.comparing(Module::getId))
        .forEach(ready::add);

    List<Module> ordered = new ArrayList<>();
    while (!ready.isEmpty()) {
      List<Module> batch = new ArrayList<>(ready);
      ready.clear();
      batch.sort(Comparator.comparing(Module::getId));

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
