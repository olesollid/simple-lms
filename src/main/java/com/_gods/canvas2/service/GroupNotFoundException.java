package com._gods.canvas2.service;

public class GroupNotFoundException extends NotFoundException {
  public GroupNotFoundException(Long id) {
    super("Group not found: " + id);
  }
}
