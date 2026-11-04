package com._gods.canvas2.service;

public class ModuleNotFoundException extends RuntimeException {
  public ModuleNotFoundException(Long id) {
    super("Module not found: " + id);
  }
}
