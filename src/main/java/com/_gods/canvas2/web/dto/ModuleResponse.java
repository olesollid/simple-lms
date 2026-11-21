package com._gods.canvas2.web.dto;

import java.util.List;

import com._gods.canvas2.model.Module;

public record ModuleResponse(Long id, Long courseId, String title, int position,
    List<Long> dependencyIds, boolean locked) {

  /** For callers that already know the module is never locked for this requester (e.g. teachers). */
  public static ModuleResponse from(Module module) {
    return from(module, false);
  }

  public static ModuleResponse from(Module module, boolean locked) {
    List<Long> dependencyIds = module.getDependencies().stream()
        .map(Module::getId)
        .sorted()
        .toList();
    return new ModuleResponse(module.getId(), module.getCourse().getId(), module.getTitle(),
        module.getPosition(), dependencyIds, locked);
  }
}
