package com._gods.simplelms.service;

/** The request conflicts with the current state of a resource. Mapped to 409 by the web layer. */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
