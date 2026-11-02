package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CODING_EXERCISE")
public class CodingExercise extends Content {

  @Column(nullable = false)
  private String repositoryUrl;

  private LocalDateTime deadline;

  @Column(nullable = false)
  private boolean lateSubmissionsAccepted;

  @Column(nullable = false)
  private boolean groupSubmission;

  protected CodingExercise() {
    super();
  }

  public CodingExercise(Module module, String title, int position, String repositoryUrl) {
    this(module, title, position, repositoryUrl, null, false, false);
  }

  public CodingExercise(Module module, String title, int position, String repositoryUrl,
      LocalDateTime deadline, boolean lateSubmissionsAccepted, boolean groupSubmission) {
    super(module, title, position);

    if (repositoryUrl == null || repositoryUrl.isBlank()) {
      throw new IllegalArgumentException("repositoryUrl must not be blank");
    }

    this.repositoryUrl = repositoryUrl.trim();
    this.deadline = deadline;
    this.lateSubmissionsAccepted = lateSubmissionsAccepted;
    this.groupSubmission = groupSubmission;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public LocalDateTime getDeadline() {
    return deadline;
  }

  public boolean isLateSubmissionsAccepted() {
    return lateSubmissionsAccepted;
  }

  public boolean isGroupSubmission() {
    return groupSubmission;
  }
}
