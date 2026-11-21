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
 * Pure grading logic: no I/O, no messaging, no persistence. A choice question is correct only if
 * the submitted set of selected option ids exactly matches the set of correct option ids (so a
 * partial match on a multi-select question is still wrong). An ordering question is correct only if
 * the submitted list of option ids is exactly the authored order.
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
      if (question.getType() == Question.Type.ORDERING) {
        List<Long> authored = question.getOptions().stream().map(AnswerOption::getId).toList();
        List<Long> submitted = question.getId() == null ? null : answers.get(question.getId());
        if (authored.equals(submitted)) {
          correctCount++;
        } else if (question.getId() != null) {
          incorrectQuestionIds.add(question.getId());
        }
        continue;
      }
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
