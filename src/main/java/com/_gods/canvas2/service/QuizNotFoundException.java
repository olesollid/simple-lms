package com._gods.canvas2.service;

public class QuizNotFoundException extends NotFoundException {
  public QuizNotFoundException(Long id) {
    super("Quiz not found: " + id);
  }
}
