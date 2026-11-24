package com._gods.simplelms.model;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
public class User {

  @Id
  @NotBlank
  @Email
  private String email;

  @NotBlank
  @Column(nullable = false)
  private String name;

  protected User() {
  }

  public User(String email, String name) {
    validateName(name);
    this.email = normalizeEmail(email);
    this.name = name.trim();
  }

  /**
   * Emails are the user's identity, so lookups must use the same normalized form
   * as stored ids: trimmed and lower-cased.
   */
  public static String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email must not be blank");
    }
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    int at = normalized.indexOf('@');
    if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
      throw new IllegalArgumentException("Email must be a valid address");
    }
    return normalized;
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Name must not be blank");
    }
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    validateName(name);
    this.name = name.trim();
  }
}
