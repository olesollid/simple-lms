package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_email", "content_id"}))
public class StudentInteraction {

  @Id 
  @GeneratedValue (strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne (optional = false)
  @JoinColumn (name = "user_email", nullable = false)
  private User user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  private LocalDateTime completedAt;


  public StudentInteraction (User user, Content content) {  
    if (user == null || content == null) {
    throw new IllegalArgumentException("User and content must not be null");
    }
    this.user = user;
    this.content = content;

  }

  protected StudentInteraction(){

  }

  public Long getId() {
    return id;
}

  public User getUser() {
    return user;
  }

  public Content getContent() {
    return content;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
}


  public void markCompleted() {
    this.completedAt = LocalDateTime.now();
}
}
