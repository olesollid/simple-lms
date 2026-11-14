package com._gods.canvas2.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.Hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

@Entity
@DiscriminatorValue("QUIZ")
public class Quiz extends Content {

  @Column(nullable = false)
  private int maxAttempts;

  @Column(nullable = false)
  private boolean showHints;

  private LocalDateTime deadline;

  @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<Question> questions = new ArrayList<>();

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

  public List<Question> getQuestions() {
    return Collections.unmodifiableList(questions);
  }

  /**
   * Forces the lazy questions collection to load now, while a Hibernate session is still open.
   * Needed before returning this entity to a caller outside the current transaction, since
   * {@link #getQuestions()} wraps the collection in {@code Collections.unmodifiableList}, which
   * {@link Hibernate#initialize} cannot see through to find the underlying lazy proxy.
   */
  public void initializeQuestions() {
    Hibernate.initialize(questions);
  }

  public Question addQuestion(String text) {
    Question question = new Question(this, text, questions.size());
    questions.add(question);
    return question;
  }
}
