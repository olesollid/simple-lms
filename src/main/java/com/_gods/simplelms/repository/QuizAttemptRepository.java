package com._gods.simplelms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  boolean existsByQuizKeyAndStudentEmailAndAttemptNumber(String quizKey, String studentEmail,
      int attemptNumber);

  /** Highest score wins; on a tie the later attempt, matching how the grade is kept in Redis. */
  Optional<QuizAttempt> findFirstByQuizKeyAndStudentEmailOrderByScoreDescAttemptNumberDesc(
      String quizKey, String studentEmail);

  Optional<QuizAttempt> findFirstByQuizKeyAndStudentEmailOrderByAttemptNumberDesc(String quizKey,
      String studentEmail);
}
