package com._gods.canvas2.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

  /** Number of attempts a student gets; {@link #UNLIMITED_ATTEMPTS} means no limit. */
  @Column(nullable = false)
  private int maxAttempts;

  public static final int UNLIMITED_ATTEMPTS = 0;

  /**
   * A quiz starts as a draft: teachers can still add, edit and remove questions, and students
   * cannot see it. Publishing freezes the questions so every attempt is graded the same way.
   */
  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean published;

  /**
   * Random, never-reused identity for this quiz's grades and attempt counts in Redis. Database IDs
   * are not safe for that: Redis outlives the database, and a fresh database (every dev restart)
   * hands out the same IDs again, so a new quiz would inherit an old quiz's attempts and grades.
   */
  @Column(name = "grade_key", unique = true, updatable = false, length = 36)
  private String gradeKey;

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

    if (maxAttempts < 0) {
      throw new IllegalArgumentException("maxAttempts must not be negative (0 means unlimited)");
    }

    HandInPolicy.validateDeadline(deadline);
    this.maxAttempts = maxAttempts;
    this.showHints = showHints;
    this.gradeKey = UUID.randomUUID().toString();
    this.deadline = deadline;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  /** Quizzes created before this key existed fall back to their ID, which is what they used. */
  public String getGradeKey() {
    return gradeKey != null ? gradeKey : String.valueOf(getId());
  }

  /** Sets or clears (null) the deadline. Times are Europe/Oslo wall-clock, like hand-ins. */
  public void updateDeadline(LocalDateTime deadline) {
    HandInPolicy.validateDeadline(deadline);
    this.deadline = deadline;
  }

  /** A quiz with a deadline closes at it: no new attempts after, exactly at the deadline is fine. */
  public boolean isClosedAt(LocalDateTime time) {
    return deadline != null && time.isAfter(deadline);
  }

  public boolean hasUnlimitedAttempts() {
    return maxAttempts == UNLIMITED_ATTEMPTS;
  }

  public boolean isPublished() {
    return published;
  }

  /** Makes the quiz available to students. Throws if it has no questions yet. */
  public void publish() {
    if (questions.isEmpty()) {
      throw new IllegalStateException("A quiz needs at least one question before it can be published");
    }
    published = true;
  }

  /** A draft quiz is never visible to students, whatever its visibility window says. */
  @Override
  public boolean isVisibleAt(LocalDateTime time) {
    return published && super.isVisibleAt(time);
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
    return addQuestion(text, null);
  }

  public Question addQuestion(String text, String hint) {
    Question question = new Question(this, text, questions.size(), hint);
    questions.add(question);
    return question;
  }

  /** Removes a question and closes the gap so positions stay 0..n-1. */
  public void removeQuestion(Question question) {
    if (!questions.remove(question)) {
      throw new IllegalArgumentException("Question does not belong to this quiz");
    }
    for (int i = 0; i < questions.size(); i++) {
      questions.get(i).setPosition(i);
    }
  }
}
