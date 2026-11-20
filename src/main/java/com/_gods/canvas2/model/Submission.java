package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A hand-in for one piece of content, owned either by a single student or -- for a
 * group-submittable {@link Assignment}/{@link CodingExercise} -- by a {@link Group} as a whole.
 * Exactly one of {@code submitter}/{@code group} is set, matching the content's own
 * {@code groupSubmission} flag. For a group submission there is one shared row per (content,
 * group): whichever member submits most recently overwrites the text and {@code submittedAt},
 * and {@code lastSubmittedBy} records who that was, for display only. Grading is manual and
 * asynchronous: a submission starts ungraded and a teacher later sets {@code grade}/
 * {@code feedback} via {@code SubmissionService#grade}. This is deliberately separate from quiz
 * submissions, which are auto-graded and never persisted as their own entity (see
 * {@code QuizSubmissionService}).
 */
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_submission_user_content", columnNames = {"user_email", "content_id"}),
    @UniqueConstraint(name = "uk_submission_group_content", columnNames = {"group_id", "content_id"}),
})
public class Submission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "user_email")
  private User submitter;

  @ManyToOne
  @JoinColumn(name = "group_id")
  private Group group;

  @ManyToOne(optional = false)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @Lob
  @Column(nullable = false)
  private String text;

  @Column(nullable = false)
  private LocalDateTime submittedAt;

  @ManyToOne(optional = false)
  @JoinColumn(name = "last_submitted_by", nullable = false)
  private User lastSubmittedBy;

  private Double grade;

  @Lob
  private String feedback;

  private LocalDateTime gradedAt;

  @Embedded
  private SubmissionFile file;

  public SubmissionFile getFile() { return file; }

  protected Submission() {
  }

  /** An individual submission, owned by the submitting student. */
  public Submission(User submitter, Content content, String text) {
    this(submitter, content, text, null);
  }

  public Submission(User submitter, Content content, String text, SubmissionFile file) {
    if (submitter == null) {
      throw new IllegalArgumentException("Submitter must not be null");
    }
    validateContentAndText(content, text, file);
    this.file = file;
    this.submitter = submitter;
    this.content = content;
    this.text = text.trim();
    this.submittedAt = LocalDateTime.now();
    this.lastSubmittedBy = submitter;
  }

  /** A group submission: owned by the group, submitted on its behalf by one of its members. */
  public Submission(Group group, User submittedBy, Content content, String text) {
    this(group, submittedBy, content, text, null);
  }

  public Submission(Group group, User submittedBy, Content content, String text, SubmissionFile file) {
    if (group == null) {
      throw new IllegalArgumentException("Group must not be null");
    }
    if (submittedBy == null) {
      throw new IllegalArgumentException("Submitted-by user must not be null");
    }
    validateContentAndText(content, text, file);
    this.file = file;
    this.group = group;
    this.content = content;
    this.text = text.trim();
    this.submittedAt = LocalDateTime.now();
    this.lastSubmittedBy = submittedBy;
  }

  private static void validateContentAndText(Content content, String text, SubmissionFile file) {
    if (content == null) {
      throw new IllegalArgumentException("Content must not be null");
    }
    if (text == null || (text.isBlank() && file == null)) {
      throw new IllegalArgumentException("Provide submission text or a file");
    }
  }

  public Long getId() {
    return id;
  }

  public User getSubmitter() {
    return submitter;
  }

  public Group getGroup() {
    return group;
  }

  public Content getContent() {
    return content;
  }

  public String getText() {
    return text;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public User getLastSubmittedBy() {
    return lastSubmittedBy;
  }

  public Double getGrade() {
    return grade;
  }

  public String getFeedback() {
    return feedback;
  }

  public LocalDateTime getGradedAt() {
    return gradedAt;
  }

  public boolean isGraded() {
    return gradedAt != null;
  }

  public void resubmit(String text, User submittedBy) {
    resubmit(text, submittedBy, null);
  }

  public void resubmit(String text, User submittedBy, SubmissionFile file) {
    validateContentAndText(content, text, file);
    if (submittedBy == null) {
      throw new IllegalArgumentException("Submitted-by user must not be null");
    }
    this.text = text.trim();
    this.file = file;
    this.submittedAt = LocalDateTime.now();
    this.lastSubmittedBy = submittedBy;
  }

  public void grade(double grade, String feedback) {
    if (grade < 0 || grade > 100) {
      throw new IllegalArgumentException("Grade must be between 0 and 100");
    }
    this.grade = grade;
    this.feedback = feedback;
    this.gradedAt = LocalDateTime.now();
  }
}
