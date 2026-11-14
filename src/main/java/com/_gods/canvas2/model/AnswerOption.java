package com._gods.canvas2.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer_options")
public class AnswerOption {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(nullable = false)
  private String text;

  @Column(nullable = false)
  private boolean correct;

  protected AnswerOption() {
  }

  public AnswerOption(Question question, String text, boolean correct) {
    if (question == null) {
      throw new IllegalArgumentException("Question must not be null");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be blank");
    }

    this.question = question;
    this.text = text.trim();
    this.correct = correct;
  }

  public Long getId() {
    return id;
  }

  public Question getQuestion() {
    return question;
  }

  public String getText() {
    return text;
  }

  public boolean isCorrect() {
    return correct;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AnswerOption that)) {
      return false;
    }
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
