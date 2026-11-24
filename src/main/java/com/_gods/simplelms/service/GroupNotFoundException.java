package com._gods.simplelms.service;

public class GroupNotFoundException extends NotFoundException {
  public GroupNotFoundException(Long id) {
    super("Group not found: " + id);
  }
}
