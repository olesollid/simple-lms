package com._gods.simplelms.service;

public class ContentNotFoundException extends NotFoundException {
  public ContentNotFoundException(Long id) {
    super("Content not found: " + id);
  }
}
