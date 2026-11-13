package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import com._gods.canvas2.web.dto.ModuleDeleteResponse;
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
      @RequestBody ModuleRequest request, Authentication authentication) {
    Module module = moduleService.createModule(courseId, request.title(), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ModuleResponse.from(module));
  }

  @GetMapping("/courses/{courseId}/modules")
  public List<ModuleResponse> getModulesByCourse(@PathVariable Long courseId,
      Authentication authentication) {
    return moduleService.getModulesByCourse(courseId, authentication.getName()).stream()
        .map(ModuleResponse::from).toList();
  }

  @GetMapping("/modules/{moduleId}")
  public ModuleResponse getModuleById(@PathVariable Long moduleId, Authentication authentication) {
    return ModuleResponse.from(moduleService.getModuleById(moduleId, authentication.getName()));
  }

  @PutMapping("/modules/{moduleId}")
  public ModuleResponse updateModule(@PathVariable Long moduleId,
      @RequestBody ModuleRequest request, Authentication authentication) {
    return ModuleResponse.from(
        moduleService.updateModuleTitle(moduleId, request.title(), authentication.getName()));
  }

  @DeleteMapping("/modules/{moduleId}")
  public ModuleDeleteResponse deleteModule(@PathVariable Long moduleId,
      Authentication authentication) {
    List<Module> affected = moduleService.deleteModule(moduleId, authentication.getName());
    return ModuleDeleteResponse.of(moduleId, affected);
  }

  @PostMapping("/modules/{moduleId}/dependencies/{dependsOnModuleId}")
  public ModuleResponse addDependency(@PathVariable Long moduleId,
      @PathVariable Long dependsOnModuleId, Authentication authentication) {
    return ModuleResponse.from(
        moduleService.addDependency(moduleId, dependsOnModuleId, authentication.getName()));
  }

  @DeleteMapping("/modules/{moduleId}/dependencies/{dependsOnModuleId}")
  public ResponseEntity<Void> removeDependency(@PathVariable Long moduleId,
      @PathVariable Long dependsOnModuleId, Authentication authentication) {
    moduleService.removeDependency(moduleId, dependsOnModuleId, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
