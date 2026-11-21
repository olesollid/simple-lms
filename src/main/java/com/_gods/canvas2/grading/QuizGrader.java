package com._gods.canvas2.grading;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.model.Quiz;

/**
 * Pure grading logic: no I/O, no messaging, no persistence. A question is correct only if the
 * submitted set of selected option ids exactly matches the set of correct option ids for that
 * question (required for multi-select questions, where a partial match is still wrong).
 */
@Component
public class QuizGrader {

  public QuizGrade grade(Quiz quiz, String studentEmail, Map<Long, List<Long>> answers) {
    return grade(quiz, studentEmail, 1, answers);
  }

  public QuizGrade grade(Quiz quiz, String studentEmail, int attemptNumber,
      Map<Long, List<Long>> answers) {
    List<Question> questions = quiz.getQuestions();
    int correctCount = 0;
    List<Long> incorrectQuestionIds = new java.util.ArrayList<>();

    for (Question question : questions) {
      Set<Long> correctOptionIds = question.getOptions().stream()
          .filter(AnswerOption::isCorrect)
          .map(AnswerOption::getId)
          .collect(java.util.stream.Collectors.toSet());

      List<Long> selected = question.getId() == null ? null : answers.get(question.getId());
      Set<Long> selectedOptionIds = selected == null ? Set.of() : Set.copyOf(selected);

      if (selectedOptionIds.equals(correctOptionIds)) {
        correctCount++;
      } else if (question.getId() != null) {
        incorrectQuestionIds.add(question.getId());
      }
    }

    int total = questions.size();
    double score = total == 0 ? 0.0 : (double) correctCount / total;

    return new QuizGrade(quiz.getId(), studentEmail, attemptNumber, correctCount, total, score,
        List.copyOf(incorrectQuestionIds), LocalDateTime.now());
  }
}
