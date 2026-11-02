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
@Table(name = "modules")
public class Module {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private int position;

  @ManyToOne(optional = true)
  @JoinColumn(name = "required_module_id", nullable = true)
  private Module requiredModule;

  protected Module() {
  }

  public Module(Course course, String title, int position) {
    this(course, title, position, null);
  }

  public Module(Course course, String title, int position, Module requiredModule) {
    if (course == null) {
      throw new IllegalArgumentException("Course must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    if (position < 0) {
      throw new IllegalArgumentException("Position must not be negative");
    }
    if (requiredModule != null && requiredModule.getCourse() != course) {
      throw new IllegalArgumentException("Required module must belong to the same course");
    }

    this.course = course;
    this.title = title.trim();
    this.position = position;
    this.requiredModule = requiredModule;
  }

  public Long getId() {
    return id;
  }

  public Course getCourse() {
    return course;
  }

  public String getTitle() {
    return title;
  }

  public int getPosition() {
    return position;
  }

  public Module getRequiredModule() {
    return requiredModule;
  }
}
