package com._gods.simplelms.web.dto;

import java.util.List;

import com._gods.simplelms.model.Module;

public record ModuleDeleteResponse(Long deletedModuleId, List<Long> affectedModuleIds) {
  public static ModuleDeleteResponse of(Long deletedModuleId, List<Module> affected) {
    List<Long> affectedModuleIds = affected.stream().map(Module::getId).sorted().toList();
    return new ModuleDeleteResponse(deletedModuleId, affectedModuleIds);
  }
}
