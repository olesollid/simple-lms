package com._gods.canvas2.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.User;

public interface StudentInteractionRepository extends JpaRepository<StudentInteraction, Long> {
  List<StudentInteraction> findByUser(User user);

  Optional<StudentInteraction> findByUserAndContent(User user, Content content);
}
