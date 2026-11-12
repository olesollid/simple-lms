package com._gods.canvas2.service;

public class ContentNotFoundException extends NotFoundException {
  public ContentNotFoundException(Long id) {
    super("Content not found: " + id);
  }
}
