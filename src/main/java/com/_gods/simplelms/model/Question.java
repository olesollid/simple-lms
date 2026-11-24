package com._gods.simplelms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hibernate.Hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  /** Marks a blank in the text of a FILL_IN question. */
  public static final String BLANK = "___";

  /**
   * How a question is answered and graded. Every question is worth one point, with no partial
   * credit.
   * <ul>
   *   <li>{@code CHOICE}: the student selects options. One correct option makes it single choice,
   *       several make it multiple choice. Correct only when exactly the correct options are
   *       selected.</li>
   *   <li>{@code ORDERING}: the student puts every option in order. The correct order is the order
   *       the teacher entered the options in; students always see them shuffled. Correct only when
   *       the whole order matches.</li>
   *   <li>{@code MATCHING}: each option is a pair, its text matched with its match text. Students
   *       see the left-hand items in order and the right-hand items shuffled, and choose a
   *       right-hand item for each left-hand one. Correct only when every pair is right (two pairs
   *       with the same right-hand text are interchangeable).</li>
   *   <li>{@code FILL_IN}: the question text contains one {@value #BLANK} per blank, and each
   *       option holds the accepted answers for one blank, separated by {@code |}. Students type an
   *       answer per blank. Correct only when every blank matches an accepted answer, ignoring case
   *       and surrounding or repeated spaces.</li>
   * </ul>
   * Choice, ordering and matching answers are lists of option ids; fill-in answers are text.
   */
  public enum Type { CHOICE, ORDERING, MATCHING, FILL_IN }
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

  /** Optional hint shown to a student who answered this question wrong. */
  @Lob
  private String hint;

  /** Existing databases get CHOICE for questions that were created before types existed. */
  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, columnDefinition = "varchar(20) default 'CHOICE'")
  private Type type = Type.CHOICE;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  private List<AnswerOption> options = new ArrayList<>();

  protected Question() {
  }

  public Question(Quiz quiz, String text, int position) {
    this(quiz, text, position, null);
  }

  public Question(Quiz quiz, String text, int position, String hint) {
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
    this.hint = normalizeHint(hint);
  }

  private static String normalizeHint(String hint) {
    return hint == null || hint.isBlank() ? null : hint.trim();
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

  public String getHint() {
    return hint;
  }

  void setPosition(int position) {
    this.position = position;
  }

  public Type getType() {
    return type;
  }

  /** A missing type means a plain choice question. */
  public void setType(Type type) {
    this.type = type == null ? Type.CHOICE : type;
  }

  /** Replaces the text and hint. Options are replaced separately via {@link #clearOptions()}. */
  public void update(String text, String hint) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text must not be blank");
    }
    this.text = text.trim();
    this.hint = normalizeHint(hint);
  }

  public void clearOptions() {
    options.clear();
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
    return addOption(text, correct, null, options.size());
  }

  public AnswerOption addOption(String text, boolean correct, String matchText, int position) {
    AnswerOption option = new AnswerOption(this, text, correct, matchText, position);
    options.add(option);
    return option;
  }

  /** The options in the order the teacher entered them (see {@link AnswerOption#getPosition()}). */
  public List<AnswerOption> getOptionsInOrder() {
    return options.stream().sorted(Comparator.comparingInt(AnswerOption::getPosition)).toList();
  }

  /** How many blanks a FILL_IN question text has. */
  public static int blankCount(String text) {
    int count = 0;
    for (int from = text.indexOf(BLANK); from >= 0; from = text.indexOf(BLANK, from + BLANK.length())) {
      count++;
    }
    return count;
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
