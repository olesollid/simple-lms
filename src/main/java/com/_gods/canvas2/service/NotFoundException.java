package com._gods.canvas2.service;

/** A requested resource does not exist. Mapped to 404 by the web layer. */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
