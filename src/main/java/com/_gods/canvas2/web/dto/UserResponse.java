package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.User;

public record UserResponse(String email, String name) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getEmail(), user.getName());
  }
}
