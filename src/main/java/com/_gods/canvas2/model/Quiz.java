package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("QUIZ")
public class Quiz extends Content {

  @Column(nullable = false)
  private int maxAttempts;

  @Column(nullable = false)
  private boolean showHints;

  private LocalDateTime deadline;

  protected Quiz() {
    super();
  }

  public Quiz(Module module, String title, int position, int maxAttempts, boolean showHints) {
    this(module, title, position, maxAttempts, showHints, null);
  }

  public Quiz(Module module, String title, int position, int maxAttempts, boolean showHints,
      LocalDateTime deadline) {
    super(module, title, position);

    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }

    this.maxAttempts = maxAttempts;
    this.showHints = showHints;
    this.deadline = deadline;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public boolean isShowHints() {
    return showHints;
  }

  public LocalDateTime getDeadline() {
    return deadline;
  }
}
