package com._gods.simplelms.service;

/** The caller is authenticated but not permitted to perform this action. Mapped to 403 by the web layer. */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
