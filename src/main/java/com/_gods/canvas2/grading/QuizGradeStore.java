package com._gods.canvas2.grading;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.QuizAttempt;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.QuizAttemptRepository;

/**
 * Keeps quiz grades. Every graded attempt is stored in the relational database, which is the
 * source of truth; Redis is a fast cache of the best and latest attempt plus the atomic attempt
 * counter. If Redis is empty (restart, eviction) values are rebuilt from the database on demand,
 * so no grade or attempt count is lost.
 *
 * <p>Per quiz and student it keeps three keys: the best graded attempt (the grade that counts),
 * the latest graded attempt (what the student just did, used to show hints), and an attempt
 * counter. The counter is incremented when an attempt is submitted -- before grading -- with an
 * atomic {@code INCR}, so concurrent submissions can never both slip under the attempt limit.
 */
@Component
public class QuizGradeStore {

  private static final Logger log = LoggerFactory.getLogger(QuizGradeStore.class);

  private final RedisTemplate<String, QuizGrade> redisTemplate;
  private final StringRedisTemplate counters;
  private final QuizAttemptRepository attempts;

  public QuizGradeStore(RedisTemplate<String, QuizGrade> redisTemplate,
      StringRedisTemplate counters, QuizAttemptRepository attempts) {
    this.redisTemplate = redisTemplate;
    this.counters = counters;
    this.attempts = attempts;
  }

  /** Records a graded attempt as the latest one, and as the best one if it scores at least as high. */
  public void save(Quiz quiz, QuizGrade grade) {
    String student = User.normalizeEmail(grade.studentEmail());
    // Idempotent: a redelivered event must not violate the unique attempt constraint.
    if (!attempts.existsByQuizKeyAndStudentEmailAndAttemptNumber(quiz.getGradeKey(), student,
        grade.attemptNumber())) {
      attempts.save(new QuizAttempt(quiz.getGradeKey(), quiz.getId(), student,
          grade.attemptNumber(), grade.correctQuestions(), grade.totalQuestions(), grade.score(),
          grade.incorrectQuestionIds(), grade.gradedAt()));
    }
    String key = key(quiz, grade.studentEmail());
    redisTemplate.opsForValue().set(key + ":latest", grade);
    QuizGrade best = read(key);
    if (best == null || grade.score() >= best.score()) {
      redisTemplate.opsForValue().set(key, grade);
    }
  }

  /** The best graded attempt -- the grade that counts. */
  public Optional<QuizGrade> find(Quiz quiz, String studentEmail) {
    String key = key(quiz, studentEmail);
    QuizGrade cached = read(key);
    if (cached != null) {
      return Optional.of(cached);
    }
    return attempts.findFirstByQuizKeyAndStudentEmailOrderByScoreDescAttemptNumberDesc(
        quiz.getGradeKey(), User.normalizeEmail(studentEmail)).map(a -> cache(key, a));
  }

  public Optional<QuizGrade> findLatest(Quiz quiz, String studentEmail) {
    String key = key(quiz, studentEmail) + ":latest";
    QuizGrade cached = read(key);
    if (cached != null) {
      return Optional.of(cached);
    }
    return attempts.findFirstByQuizKeyAndStudentEmailOrderByAttemptNumberDesc(
        quiz.getGradeKey(), User.normalizeEmail(studentEmail)).map(a -> cache(key, a));
  }

  /** Rebuilds a grade from the database and puts it back into Redis. */
  private QuizGrade cache(String key, QuizAttempt a) {
    QuizGrade grade = new QuizGrade(a.getQuizId(), a.getStudentEmail(), a.getAttemptNumber(),
        a.getCorrectQuestions(), a.getTotalQuestions(), a.getScore(),
        List.copyOf(a.getIncorrectQuestionIds()), a.getGradedAt());
    redisTemplate.opsForValue().set(key, grade);
    return grade;
  }

  /**
   * A value written by an older version of the app may no longer match {@link QuizGrade}. It is
   * treated as "no grade" rather than failing the request.
   */
  private QuizGrade read(String key) {
    try {
      return redisTemplate.opsForValue().get(key);
    } catch (SerializationException e) {
      log.warn("Ignoring unreadable quiz grade stored under {}", key);
      return null;
    }
  }

  public int attemptsUsed(Quiz quiz, String studentEmail) {
    String value = counters.opsForValue().get(attemptsKey(quiz, studentEmail));
    return value == null ? gradedAttempts(quiz, studentEmail) : Integer.parseInt(value);
  }

  /** The number of the latest graded attempt, i.e. how many attempts the database knows about. */
  private int gradedAttempts(Quiz quiz, String studentEmail) {
    return attempts.findFirstByQuizKeyAndStudentEmailOrderByAttemptNumberDesc(
        quiz.getGradeKey(), User.normalizeEmail(studentEmail))
        .map(QuizAttempt::getAttemptNumber).orElse(0);
  }

  /**
   * Claims the next attempt and returns its number (1-based), or empty if the student has used
   * all of the quiz's attempts. A quiz with {@code maxAttempts == 0} has unlimited attempts.
   */
  public Optional<Integer> reserveAttempt(Quiz quiz, String studentEmail) {
    String key = attemptsKey(quiz, studentEmail);
    // After a Redis restart the counter is gone: resume from the attempts already graded.
    if (counters.opsForValue().get(key) == null) {
      int graded = gradedAttempts(quiz, studentEmail);
      if (graded > 0) {
        counters.opsForValue().setIfAbsent(key, String.valueOf(graded));
      }
    }
    Long attempt = counters.opsForValue().increment(key);
    if (attempt == null) {
      throw new IllegalStateException("Redis did not return an attempt count");
    }
    if (!quiz.hasUnlimitedAttempts() && attempt > quiz.getMaxAttempts()) {
      counters.opsForValue().decrement(key);
      return Optional.empty();
    }
    return Optional.of(attempt.intValue());
  }

  /** Gives back an attempt that was reserved but never submitted for grading. */
  public void releaseAttempt(Quiz quiz, String studentEmail) {
    counters.opsForValue().decrement(attemptsKey(quiz, studentEmail));
  }

  /** Keyed by the quiz's never-reused grade key, not its database ID -- see {@link Quiz}. */
  private static String key(Quiz quiz, String studentEmail) {
    return "quiz:" + quiz.getGradeKey() + ":student:" + User.normalizeEmail(studentEmail);
  }

  private static String attemptsKey(Quiz quiz, String studentEmail) {
    return key(quiz, studentEmail) + ":attempts";
  }
}
