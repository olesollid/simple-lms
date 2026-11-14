package com._gods.canvas2.grading;

import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com._gods.canvas2.model.User;

/** Thin wrapper around Redis so the rest of the app never touches RedisTemplate directly. */
@Component
public class QuizGradeStore {

  private final RedisTemplate<String, QuizGrade> redisTemplate;

  public QuizGradeStore(RedisTemplate<String, QuizGrade> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void save(QuizGrade grade) {
    redisTemplate.opsForValue().set(key(grade.quizId(), grade.studentEmail()), grade);
  }

  public Optional<QuizGrade> find(Long quizId, String studentEmail) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(key(quizId, studentEmail)));
  }

  private static String key(Long quizId, String studentEmail) {
    return "quiz:" + quizId + ":student:" + User.normalizeEmail(studentEmail);
  }
}
