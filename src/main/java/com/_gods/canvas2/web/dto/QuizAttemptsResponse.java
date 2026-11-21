package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com._gods.canvas2.grading.QuizGrade;

/**
 * A student's own progress on a quiz. {@code maxAttempts == 0} means unlimited. {@code best} is
 * the grade that counts; {@code latest} is the most recently graded attempt. Either is null until
 * the first attempt has been graded. {@code closed} is true once the Europe/Oslo deadline has
 * passed; computed on the server so the browser's time zone does not matter. {@code hints} maps
 * question id to hint, only for questions the latest attempt got wrong (and that have a hint), so a
 * student never sees a hint before answering. When {@code resultsVisible} is false the teacher
 * has not published the results yet, so {@code best}, {@code latest} and {@code hints} are empty. When {@code attemptsUsed} is ahead of
 * {@code latest.attemptNumber()}, the newest attempt is still being graded.
 */
public record QuizAttemptsResponse(int attemptsUsed, int maxAttempts, LocalDateTime deadline,
    boolean closed, boolean resultsVisible, QuizGrade best, QuizGrade latest,
    Map<Long, String> hints) {
}
