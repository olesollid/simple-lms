package com._gods.simplelms.service;

public class ModuleNotFoundException extends NotFoundException {
  public ModuleNotFoundException(Long id) {
    super("Module not found: " + id);
  }
}
