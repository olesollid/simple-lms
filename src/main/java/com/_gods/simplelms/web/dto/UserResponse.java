package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.User;

public record UserResponse(String email, String name) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getEmail(), user.getName());
  }
}
