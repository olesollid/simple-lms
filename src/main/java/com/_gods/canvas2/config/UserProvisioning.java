package com._gods.canvas2.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com._gods.canvas2.model.User;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.service.UserService;

/** Looks up or creates the {@link User} for a verified email resolved from an OAuth2/OIDC login. */
@Component
class UserProvisioning {

  private final UserService userService;

  UserProvisioning(UserService userService) {
    this.userService = userService;
  }

  User provision(String email, String name) {
    String normalizedEmail = User.normalizeEmail(email);
    try {
      return userService.getUserByEmail(normalizedEmail);
    } catch (NotFoundException e) {
      try {
        return userService.createUser(normalizedEmail, name);
      } catch (DataIntegrityViolationException raceLost) {
        return userService.getUserByEmail(normalizedEmail);
      }
    }
  }
}
