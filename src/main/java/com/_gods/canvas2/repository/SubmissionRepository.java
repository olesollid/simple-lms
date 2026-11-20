package com._gods.canvas2.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.User;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
  Optional<Submission> findByContentAndSubmitter(Content content, User submitter);

  Optional<Submission> findByContentAndGroup(Content content, Group group);

  List<Submission> findByContent(Content content);

  boolean existsByGroup(Group group);
}
