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

  /**
   * Where the teacher put this option. For ORDERING it is the correct order; for MATCHING and
   * FILL_IN it is the pair or blank number. Never rely on the database id for this: ids are handed
   * out in a random order for the types where the order is the answer, so a student cannot read
   * the answer off the ids. Existing options default to 0, which only matters for CHOICE.
   */
  @Column(nullable = false, columnDefinition = "integer default 0")
  private int position;

  /** MATCHING only: what {@link #getText()} (the left-hand item) is matched with. */
  @Column(name = "match_text")
  private String matchText;

  protected AnswerOption() {
  }

  public AnswerOption(Question question, String text, boolean correct) {
    this(question, text, correct, null, 0);
  }

  public AnswerOption(Question question, String text, boolean correct, String matchText,
      int position) {
    if (question == null) {
      throw new IllegalArgumentException("Question must not be null");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be blank");
    }

    this.question = question;
    this.text = text.trim();
    this.correct = correct;
    this.position = position;
    this.matchText = matchText == null || matchText.isBlank() ? null : matchText.trim();
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

  public int getPosition() {
    return position;
  }

  public String getMatchText() {
    return matchText;
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
