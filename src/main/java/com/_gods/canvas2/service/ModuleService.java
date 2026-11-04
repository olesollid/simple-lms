package com._gods.canvas2.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.springframework.stereotype.Service;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
public class ModuleService {

  private final ModuleRepository moduleRepository;
  private final CourseRepository courseRepository;

  public ModuleService(ModuleRepository moduleRepository, CourseRepository courseRepository) {
    this.moduleRepository = moduleRepository;
    this.courseRepository = courseRepository;
  }

  public Module createModule(Long courseId, String title) {
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    return moduleRepository.save(new Module(course, title));
  }

  public List<Module> getModulesByCourse(Long courseId) {
    Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
    return topologicalSort(moduleRepository.findByCourse(course));
  }

  public Module getModuleById(Long id) {
    return moduleRepository.findById(id)
        .orElseThrow(() -> new ModuleNotFoundException(id));
  }

  public Module updateModuleTitle(Long id, String title) {
    Module module = getModuleById(id);
    module.setTitle(title);
    return moduleRepository.save(module);
  }

  public void deleteModule(Long id) {
    Module module = getModuleById(id);
    boolean hasDependents = moduleRepository.findByCourse(module.getCourse()).stream()
        .anyMatch(other -> other.getDependencies().contains(module));
    if (hasDependents) {
      throw new ModuleHasDependentsException(id);
    }
    moduleRepository.delete(module);
  }

  public Module addDependency(Long moduleId, Long dependsOnModuleId) {
    Module module = getModuleById(moduleId);
    Module dependsOn = getModuleById(dependsOnModuleId);
    module.addDependency(dependsOn);
    return moduleRepository.save(module);
  }

  public Module removeDependency(Long moduleId, Long dependsOnModuleId) {
    Module module = getModuleById(moduleId);
    Module dependsOn = getModuleById(dependsOnModuleId);
    module.removeDependency(dependsOn);
    return moduleRepository.save(module);
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
