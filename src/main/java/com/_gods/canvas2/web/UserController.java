package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.service.UserService;
import com._gods.canvas2.web.dto.EnrollmentResponse;
import com._gods.canvas2.web.dto.UserRequest;
import com._gods.canvas2.web.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final EnrollmentService enrollmentService;

  public UserController(UserService userService, EnrollmentService enrollmentService) {
    this.userService = userService;
    this.enrollmentService = enrollmentService;
  }

  @GetMapping("/{email}")
  public UserResponse getUserByEmail(@PathVariable String email) {
    return UserResponse.from(userService.getUserByEmail(email));
  }

  @GetMapping("/me")
  public UserResponse getCurrentUser(Authentication authentication) {
    return UserResponse.from(userService.getUserByEmail(authentication.getName()));
  }

  @PutMapping("/{email}")
  public UserResponse updateUser(@PathVariable String email, @RequestBody UserRequest request,
      Authentication authentication) {
    return UserResponse.from(
        userService.updateUserName(email, request.name(), authentication.getName()));
  }

  @DeleteMapping("/{email}")
  public ResponseEntity<Void> deleteUser(@PathVariable String email,
      Authentication authentication) {
    userService.deleteUser(email, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{email}/enrollments")
  public List<EnrollmentResponse> getEnrollmentsByUser(@PathVariable String email) {
    return enrollmentService.getEnrollmentsByUser(email).stream()
        .map(EnrollmentResponse::from)
        .toList();
  }
}
