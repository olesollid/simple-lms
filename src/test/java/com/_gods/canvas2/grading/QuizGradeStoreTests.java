package com._gods.canvas2.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Term;

class QuizGradeStoreTests {

  private static final String STUDENT = "student@example.com";

  private ValueOperations<String, QuizGrade> grades;
  private ValueOperations<String, String> counters;
  private QuizGradeStore store;
  private Quiz quiz;
  private String key;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    RedisTemplate<String, QuizGrade> gradeTemplate = mock(RedisTemplate.class);
    StringRedisTemplate counterTemplate = mock(StringRedisTemplate.class);
    grades = mock(ValueOperations.class);
    counters = mock(ValueOperations.class);
    when(gradeTemplate.opsForValue()).thenReturn(grades);
    when(counterTemplate.opsForValue()).thenReturn(counters);
    store = new QuizGradeStore(gradeTemplate, counterTemplate);
    quiz = quiz(2);
    key = "quiz:" + quiz.getGradeKey() + ":student:" + STUDENT;
  }

  private static Quiz quiz(int maxAttempts) {
    Module module = new Module(new Course("Intro", "desc", Term.AUTUMN, 2026), "Week 1");
    return new Quiz(module, "Quiz", 0, maxAttempts, false);
  }

  private static QuizGrade grade(int attempt, double score) {
    return new QuizGrade(1L, STUDENT, attempt, 0, 1, score, List.of(), LocalDateTime.now());
  }

  @Test
  void aNewQuizNeverSharesGradesWithAnOlderQuizThatHadTheSameId() {
    Quiz older = quiz(2);
    Quiz newer = quiz(2);

    assertNotEquals(older.getGradeKey(), newer.getGradeKey());
    when(grades.get("quiz:" + older.getGradeKey() + ":student:" + STUDENT)).thenReturn(grade(1, 1.0));
    assertEquals(Optional.empty(), store.find(newer, STUDENT));
  }

  @Test
  void treatsAGradeStoredInAnOldFormatAsNoGrade() {
    when(grades.get(key)).thenThrow(new SerializationException("missing attemptNumber"));

    assertEquals(Optional.empty(), store.find(quiz, STUDENT));
  }

  @Test
  void overwritesAnUnreadableBestGradeWithTheNewOne() {
    when(grades.get(key)).thenThrow(new SerializationException("missing attemptNumber"));
    QuizGrade grade = grade(1, 0.5);

    store.save(quiz, grade);

    verify(grades).set(key, grade);
    verify(grades).set(key + ":latest", grade);
  }

  @Test
  void keepsTheBestGradeWhenALaterAttemptScoresLower() {
    when(grades.get(key)).thenReturn(grade(1, 1.0));
    QuizGrade worse = grade(2, 0.5);

    store.save(quiz, worse);

    verify(grades).set(key + ":latest", worse);
    verify(grades, never()).set(key, worse);
  }

  @Test
  void refusesAnAttemptBeyondTheLimitAndGivesTheCountBack() {
    when(counters.increment(key + ":attempts")).thenReturn(3L);

    assertTrue(store.reserveAttempt(quiz, STUDENT).isEmpty());
    verify(counters).decrement(key + ":attempts");
  }

  @Test
  void unlimitedAttemptsAreNeverRefused() {
    when(counters.increment(any())).thenReturn(99L);

    assertEquals(Optional.of(99), store.reserveAttempt(quiz(Quiz.UNLIMITED_ATTEMPTS), STUDENT));
  }
}
