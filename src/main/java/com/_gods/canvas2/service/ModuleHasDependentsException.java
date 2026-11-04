package com._gods.canvas2.service;

public class ModuleHasDependentsException extends RuntimeException {
  public ModuleHasDependentsException(Long id) {
    super("Module " + id + " is required by other modules and cannot be deleted until they are updated");
  }
}
