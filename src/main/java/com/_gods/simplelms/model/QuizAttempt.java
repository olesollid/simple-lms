package com._gods.simplelms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One graded quiz attempt, stored in the relational database so grades survive a Redis restart.
 * Redis only caches the best and latest attempt and holds the atomic attempt counter.
 *
 * <p>Deliberately has no foreign key to the quiz: like the Redis keys it is identified by the
 * quiz's never-reused grade key, so a deleted quiz can never leak its grades into a new one.
 */
@Entity
@Table(name = "quiz_attempt",
    uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_key", "student_email", "attempt_number"}))
public class QuizAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "quiz_key", nullable = false)
  private String quizKey;

  private Long quizId;

  @Column(name = "student_email", nullable = false)
  private String studentEmail;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  private int correctQuestions;
  private int totalQuestions;
  private double score;
  private LocalDateTime gradedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "quiz_attempt_incorrect_question",
      joinColumns = @JoinColumn(name = "attempt_id"))
  @OrderColumn(name = "idx")
  @Column(name = "question_id")
  private List<Long> incorrectQuestionIds = new ArrayList<>();

  protected QuizAttempt() {
  }

  public QuizAttempt(String quizKey, Long quizId, String studentEmail, int attemptNumber,
      int correctQuestions, int totalQuestions, double score, List<Long> incorrectQuestionIds,
      LocalDateTime gradedAt) {
    this.quizKey = quizKey;
    this.quizId = quizId;
    this.studentEmail = studentEmail;
    this.attemptNumber = attemptNumber;
    this.correctQuestions = correctQuestions;
    this.totalQuestions = totalQuestions;
    this.score = score;
    this.incorrectQuestionIds = new ArrayList<>(incorrectQuestionIds);
    this.gradedAt = gradedAt;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public double getScore() {
    return score;
  }

  public Long getQuizId() {
    return quizId;
  }

  public String getStudentEmail() {
    return studentEmail;
  }

  public int getCorrectQuestions() {
    return correctQuestions;
  }

  public int getTotalQuestions() {
    return totalQuestions;
  }

  public List<Long> getIncorrectQuestionIds() {
    return incorrectQuestionIds;
  }

  public LocalDateTime getGradedAt() {
    return gradedAt;
  }
}
