package com._gods.canvas2.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "group_memberships",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_email", "group_id"}
    )
  )
public class GroupMembership {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_email", nullable = false)
  private User user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private Group group;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime joinedAt;

  protected GroupMembership() {
  }

  public GroupMembership(User user, Group group) {
    if (user == null) {
      throw new IllegalArgumentException("User must not be null");
    }
    if (group == null) {
      throw new IllegalArgumentException("Group must not be null");
    }

    this.user = user;
    this.group = group;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Group getGroup() {
    return group;
  }

  public LocalDateTime getJoinedAt() {
    return joinedAt;
  }
}
