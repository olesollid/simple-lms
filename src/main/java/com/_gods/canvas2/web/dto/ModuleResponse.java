package com._gods.canvas2.web.dto;

import java.util.List;

import com._gods.canvas2.model.Module;

public record ModuleResponse(Long id, Long courseId, String title, List<Long> dependencyIds) {
  public static ModuleResponse from(Module module) {
    List<Long> dependencyIds = module.getDependencies().stream()
        .map(Module::getId)
        .sorted()
        .toList();
    return new ModuleResponse(module.getId(), module.getCourse().getId(), module.getTitle(),
        dependencyIds);
  }
}
