package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

@Entity
@DiscriminatorValue("ASSIGNMENT")
public class Assignment extends Content {

  @Lob
  @Column(nullable = false)
  private String instructions;

  private LocalDateTime deadline;

  @Column(nullable = false)
  private boolean lateSubmissionsAccepted;

  @Column(nullable = false)
  private boolean groupSubmission;

  protected Assignment() {
    super();
  }

  public Assignment(Module module, String title, int position, String instructions) {
    this(module, title, position, instructions, null, false, false);
  }

  public Assignment(Module module, String title, int position, String instructions,
      LocalDateTime deadline, boolean lateSubmissionsAccepted, boolean groupSubmission) {
    super(module, title, position);

    if (instructions == null || instructions.isBlank()) {
      throw new IllegalArgumentException("instructions must not be blank");
    }

    this.instructions = instructions;
    updateSubmissionPolicy(deadline, lateSubmissionsAccepted);
    this.groupSubmission = groupSubmission;
  }

  public String getInstructions() {
    return instructions;
  }

  public LocalDateTime getDeadline() {
    return deadline;
  }

  public void updateSubmissionPolicy(LocalDateTime deadline, boolean acceptsLate) {
    HandInPolicy.validateDeadline(deadline);
    this.deadline = deadline;
    this.lateSubmissionsAccepted = acceptsLate;
  }

  public boolean isLateSubmissionsAccepted() {
    return lateSubmissionsAccepted;
  }

  public boolean isGroupSubmission() {
    return groupSubmission;
  }
}
