package com._gods.canvas2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.Hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class Question {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Lob
  @Column(nullable = false)
  private String text;

  @Column(nullable = false)
  private int position;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  private List<AnswerOption> options = new ArrayList<>();

  protected Question() {
  }

  public Question(Quiz quiz, String text, int position) {
    if (quiz == null) {
      throw new IllegalArgumentException("Quiz must not be null");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be blank");
    }
    if (position < 0) {
      throw new IllegalArgumentException("Position must not be negative");
    }

    this.quiz = quiz;
    this.text = text.trim();
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public Quiz getQuiz() {
    return quiz;
  }

  public String getText() {
    return text;
  }

  public int getPosition() {
    return position;
  }

  public List<AnswerOption> getOptions() {
    return Collections.unmodifiableList(options);
  }

  /**
   * Forces the lazy options collection to load now, while a Hibernate session is still open.
   * Needed before returning this entity to a caller outside the current transaction, since
   * {@link #getOptions()} wraps the collection in {@code Collections.unmodifiableList}, which
   * {@link Hibernate#initialize} cannot see through to find the underlying lazy proxy.
   */
  public void initializeOptions() {
    Hibernate.initialize(options);
  }

  public AnswerOption addOption(String text, boolean correct) {
    AnswerOption option = new AnswerOption(this, text, correct);
    options.add(option);
    return option;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Question that)) {
      return false;
    }
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
