package com._gods.canvas2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 5000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Term term;

  @Column(name = "academic_year", nullable = false)
  private int year;

  protected Course() {
  }

  public Course(String title, String description, Term term, int year) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    if (description == null) {
      throw new IllegalArgumentException("Description must not be null");
    }
    if (term == null) {
      throw new IllegalArgumentException("Term must not be null");
    }
    if (year < 1) {
      throw new IllegalArgumentException("Year must be positive");
    }

    this.title = title.trim();
    this.description = description;
    this.term = term;
    this.year = year;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Term getTerm() {
    return term;
  }

  public int getYear() {
    return year;
  }
}
