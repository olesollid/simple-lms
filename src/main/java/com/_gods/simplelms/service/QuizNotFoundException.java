package com._gods.simplelms.service;

public class QuizNotFoundException extends NotFoundException {
  public QuizNotFoundException(Long id) {
    super("Quiz not found: " + id);
  }
}
