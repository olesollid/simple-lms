package com._gods.canvas2.model;

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
    validate(title, description, term, year);

    this.title = title.trim();
    this.description = description;
    this.term = term;
    this.year = year;
  }

  private static void validate(String title, String description, Term term, int year) {
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

  public void setTitle(String title) {
    validate(title, this.description, this.term, this.year);
    this.title = title.trim();
  }

  public void setDescription(String description) {
    validate(this.title, description, this.term, this.year);
    this.description = description;
  }

  public void setTerm(Term term) {
    validate(this.title, this.description, term, this.year);
    this.term = term;
  }

  public void setYear(int year) {
    validate(this.title, this.description, this.term, year);
    this.year = year;
  }
}
