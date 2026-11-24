package com._gods.simplelms.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.EnrollmentRepository;
import com._gods.simplelms.repository.GroupMembershipRepository;
import com._gods.simplelms.repository.StudentInteractionRepository;
import com._gods.simplelms.repository.UserRepository;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final GroupMembershipRepository groupMembershipRepository;
  private final StudentInteractionRepository studentInteractionRepository;

  public UserService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
      GroupMembershipRepository groupMembershipRepository,
      StudentInteractionRepository studentInteractionRepository) {
    this.userRepository = userRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.groupMembershipRepository = groupMembershipRepository;
    this.studentInteractionRepository = studentInteractionRepository;
  }

  public User createUser(String email, String name) {
    User user = new User(email, name);
    if (userRepository.existsById(user.getEmail())) {
      throw new ConflictException("User already exists: " + user.getEmail());
    }
    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public List<User> getAllUsers() {
    return userRepository.findAll();
  }

  @Transactional(readOnly = true)
  public User getUserByEmail(String email) {
    String normalized = User.normalizeEmail(email);
    return userRepository.findById(normalized)
        .orElseThrow(() -> new NotFoundException("User not found: " + normalized));
  }

  public User updateUserName(String email, String name, String requesterEmail) {
    requireSelf(email, requesterEmail);
    User user = getUserByEmail(email);
    user.setName(name);
    return userRepository.save(user);
  }

  public void deleteUser(String email, String requesterEmail) {
    requireSelf(email, requesterEmail);
    User user = getUserByEmail(email);
    boolean hasDependents = enrollmentRepository.existsByUser(user)
        || groupMembershipRepository.existsByUser(user)
        || studentInteractionRepository.existsByUser(user);
    if (hasDependents) {
      throw new ConflictException("User " + user.getEmail()
          + " has enrollments, group memberships or interactions and cannot be deleted until they are removed");
    }
    userRepository.delete(user);
  }

  private void requireSelf(String email, String requesterEmail) {
    if (!User.normalizeEmail(email).equals(User.normalizeEmail(requesterEmail))) {
      throw new ForbiddenException("Users may only modify their own account");
    }
  }
}
