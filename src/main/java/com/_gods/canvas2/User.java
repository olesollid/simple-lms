package com._gods.canvas2;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity 
public class User {
  
  @Id 
  private String email;
  
  private String name; 
  
  protected User() {

  }

  public User (String email, String name) {
    this.email = email;
    this.name = name;
  }

  public String getEmail() {
    return email;
}
  public String getName() {
    return name;
} 
}
