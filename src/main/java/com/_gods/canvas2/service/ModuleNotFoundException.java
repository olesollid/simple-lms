package com._gods.canvas2.service;

public class ModuleNotFoundException extends NotFoundException {
  public ModuleNotFoundException(Long id) {
    super("Module not found: " + id);
  }
}
