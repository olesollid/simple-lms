package com._gods.canvas2;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
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
    if(email == null || email.isBlank()) {
      throw new IllegalArgumentException("email can not be empty");
    }
    if(name == null || name.isBlank()) {
      throw new IllegalArgumentException("name can not be empty");
    }    

    this.email = email;
    this.name = name.trim();
  }

  public String getEmail() {
    return email;
  }
  public String getName() {
    return name;
  }
}
