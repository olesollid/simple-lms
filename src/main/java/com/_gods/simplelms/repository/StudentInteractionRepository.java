package com._gods.simplelms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Content;
import com._gods.simplelms.model.StudentInteraction;
import com._gods.simplelms.model.User;

public interface StudentInteractionRepository extends JpaRepository<StudentInteraction, Long> {
  List<StudentInteraction> findByUser(User user);

  Optional<StudentInteraction> findByUserAndContent(User user, Content content);

  List<StudentInteraction> findByContent(Content content);

  boolean existsByUser(User user);
}
