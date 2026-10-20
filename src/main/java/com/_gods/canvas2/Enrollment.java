package com._gods.canvas2;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity 
@Table(
    name = "enrollments",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_email", "course_id"}
    )
  )
public class Enrollment {
  @ManyToOne (optional = false)
  @JoinColumn (name = "user_email", nullable = false)
  private User user;
  
  @ManyToOne (optional = false)
  @JoinColumn  (name = "course_id", nullable = false)
  private Course course;
  
  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; 
  
  @Enumerated(EnumType.STRING)
  @Column (nullable = false)
  private Role role ;
  
  @CreationTimestamp 
  @Column (nullable = false, updatable = false)
  private LocalDateTime enrolledAt;


  public Enrollment (User user, Course course, Role role) {
    this.user = user;
    this.course = course;
    this.role = role;
  }

  protected Enrollment(){
  }

  public Long getId() {
      return id;
  }

  public User getUser() {
      return user;
  }

  public Course getCourse() {
      return course;
  }

  public Role getRole() {
      return role;
  }

  public LocalDateTime getEnrolledAt() {
      return enrolledAt;
  }
}
