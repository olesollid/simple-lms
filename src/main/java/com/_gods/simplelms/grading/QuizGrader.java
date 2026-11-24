package com._gods.simplelms.grading;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com._gods.simplelms.model.AnswerOption;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.model.Quiz;

/**
 * Pure grading logic: no I/O, no messaging, no persistence. Every question is worth one point and
 * is right or wrong as a whole; see {@link Question.Type} for what "right" means per type.
 */
@Component
public class QuizGrader {

  public QuizGrade grade(Quiz quiz, String studentEmail, Map<Long, List<Long>> answers) {
    return grade(quiz, studentEmail, 1, answers, null);
  }

  public QuizGrade grade(Quiz quiz, String studentEmail, int attemptNumber,
      Map<Long, List<Long>> answers) {
    return grade(quiz, studentEmail, attemptNumber, answers, null);
  }

  /**
   * @param answers option ids per question id (choice, ordering and matching questions)
   * @param textAnswers typed answers per question id, one per blank (fill-in questions); may be null
   */
  public QuizGrade grade(Quiz quiz, String studentEmail, int attemptNumber,
      Map<Long, List<Long>> answers, Map<Long, List<String>> textAnswers) {
    List<Question> questions = quiz.getQuestions();
    int correctCount = 0;
    List<Long> incorrectQuestionIds = new ArrayList<>();

    for (Question question : questions) {
      Long id = question.getId();
      boolean correct = id != null && isCorrect(question, answers.get(id),
          textAnswers == null ? null : textAnswers.get(id));
      if (correct) {
        correctCount++;
      } else if (id != null) {
        incorrectQuestionIds.add(id);
      }
    }

    int total = questions.size();
    double score = total == 0 ? 0.0 : (double) correctCount / total;

    return new QuizGrade(quiz.getId(), studentEmail, attemptNumber, correctCount, total, score,
        List.copyOf(incorrectQuestionIds), LocalDateTime.now());
  }

  private static boolean isCorrect(Question question, List<Long> optionIds, List<String> texts) {
    return switch (question.getType()) {
      case CHOICE -> choiceIsCorrect(question, optionIds);
      case ORDERING -> orderingIsCorrect(question, optionIds);
      case MATCHING -> matchingIsCorrect(question, optionIds);
      case FILL_IN -> fillInIsCorrect(question, texts);
    };
  }

  /** Exactly the correct options, no more and no fewer. */
  private static boolean choiceIsCorrect(Question question, List<Long> selected) {
    Set<Long> correct = question.getOptions().stream()
        .filter(AnswerOption::isCorrect)
        .map(AnswerOption::getId)
        .collect(Collectors.toSet());
    return (selected == null ? Set.<Long>of() : Set.copyOf(selected)).equals(correct);
  }

  /** Every option once, in the authored order. */
  private static boolean orderingIsCorrect(Question question, List<Long> submitted) {
    List<Long> authored = question.getOptionsInOrder().stream().map(AnswerOption::getId).toList();
    return authored.equals(submitted);
  }

  /**
   * One right-hand option id per left-hand item, in order. A chosen right-hand item is right when
   * its text equals the text that belongs to that left-hand item, so identical right-hand texts
   * are interchangeable.
   */
  private static boolean matchingIsCorrect(Question question, List<Long> chosen) {
    List<AnswerOption> pairs = question.getOptionsInOrder();
    if (chosen == null || chosen.size() != pairs.size()) {
      return false;
    }
    Map<Long, AnswerOption> byId = question.getOptions().stream()
        .collect(Collectors.toMap(AnswerOption::getId, Function.identity()));
    for (int i = 0; i < pairs.size(); i++) {
      AnswerOption picked = byId.get(chosen.get(i));
      if (picked == null || !Objects.equals(picked.getMatchText(), pairs.get(i).getMatchText())) {
        return false;
      }
    }
    return true;
  }

  /** One typed answer per blank, each matching one of that blank's accepted answers. */
  private static boolean fillInIsCorrect(Question question, List<String> typed) {
    List<AnswerOption> blanks = question.getOptionsInOrder();
    if (typed == null || typed.size() != blanks.size()) {
      return false;
    }
    for (int i = 0; i < blanks.size(); i++) {
      String answer = typed.get(i);
      boolean accepted = answer != null && Arrays.stream(blanks.get(i).getText().split("\\|"))
          .map(QuizGrader::normalize)
          .anyMatch(candidate -> !candidate.isEmpty() && candidate.equals(normalize(answer)));
      if (!accepted) {
        return false;
      }
    }
    return true;
  }

  /** Ignores case and surrounding or repeated whitespace. */
  static String normalize(String text) {
    return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
