package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.model.Module;
import com._gods.canvas2.service.ModuleService;
import com._gods.canvas2.web.dto.ModuleRequest;
import com._gods.canvas2.web.dto.ModuleResponse;

@RestController
@RequestMapping("/api")
public class ModuleController {

  private final ModuleService moduleService;

  public ModuleController(ModuleService moduleService) {
    this.moduleService = moduleService;
  }

  @PostMapping("/courses/{courseId}/modules")
  public ResponseEntity<ModuleResponse> createModule(@PathVariable Long courseId,
      @RequestBody ModuleRequest request) {
    Module module = moduleService.createModule(courseId, request.title());
    return ResponseEntity.status(HttpStatus.CREATED).body(ModuleResponse.from(module));
  }

  @GetMapping("/courses/{courseId}/modules")
  public List<ModuleResponse> getModulesByCourse(@PathVariable Long courseId) {
    return moduleService.getModulesByCourse(courseId).stream().map(ModuleResponse::from).toList();
  }

  @GetMapping("/modules/{moduleId}")
  public ModuleResponse getModuleById(@PathVariable Long moduleId) {
    return ModuleResponse.from(moduleService.getModuleById(moduleId));
  }

  @PutMapping("/modules/{moduleId}")
  public ModuleResponse updateModule(@PathVariable Long moduleId,
      @RequestBody ModuleRequest request) {
    return ModuleResponse.from(moduleService.updateModuleTitle(moduleId, request.title()));
  }

  @DeleteMapping("/modules/{moduleId}")
  public ResponseEntity<Void> deleteModule(@PathVariable Long moduleId) {
    moduleService.deleteModule(moduleId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/modules/{moduleId}/dependencies/{dependsOnModuleId}")
  public ModuleResponse addDependency(@PathVariable Long moduleId,
      @PathVariable Long dependsOnModuleId) {
    return ModuleResponse.from(moduleService.addDependency(moduleId, dependsOnModuleId));
  }

  @DeleteMapping("/modules/{moduleId}/dependencies/{dependsOnModuleId}")
  public ResponseEntity<Void> removeDependency(@PathVariable Long moduleId,
      @PathVariable Long dependsOnModuleId) {
    moduleService.removeDependency(moduleId, dependsOnModuleId);
    return ResponseEntity.noContent().build();
  }
}
