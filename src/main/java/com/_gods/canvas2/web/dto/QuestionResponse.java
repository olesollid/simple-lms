package com._gods.canvas2.web.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com._gods.canvas2.model.AnswerOption;
import com._gods.canvas2.model.Question;

/**
 * A question as students see it: no correct flags, no accepted answers and no hint. Hints are only
 * revealed for the questions a student got wrong, through {@link QuizAttemptsResponse#hints()}.
 *
 * <p>What {@code options} and {@code matches} hold depends on {@code type}:
 * <ul>
 *   <li>CHOICE: the options to select from. {@code matches} is empty.</li>
 *   <li>ORDERING: the options, shuffled; the answer is every option id in the chosen order.</li>
 *   <li>MATCHING: {@code options} are the left-hand items in order, with their position as id;
 *       {@code matches} are the right-hand items, shuffled, with their own ids. The answer has one
 *       right-hand id per left-hand item, in the left-hand order.</li>
 *   <li>FILL_IN: both are empty. The text has one {@code ___} per blank; the answer is text and
 *       goes in {@code textAnswers} of the submission.</li>
 * </ul>
 * Option ids of ORDERING and MATCHING questions are handed out in a random order, so sorting them
 * does not reveal the answer.
 */
public record QuestionResponse(Long id, String text, int position, Question.Type type,
    List<AnswerOptionResponse> options, List<AnswerOptionResponse> matches) {

  public static QuestionResponse from(Question question) {
    return switch (question.getType()) {
      case ORDERING -> {
        List<AnswerOptionResponse> authored = question.getOptionsInOrder().stream()
            .map(AnswerOptionResponse::from).toList();
        yield of(question, shuffledDifferently(authored), List.of());
      }
      case MATCHING -> {
        List<AnswerOption> ordered = question.getOptionsInOrder();
        List<AnswerOptionResponse> left = IntStream.range(0, ordered.size())
            .mapToObj(i -> new AnswerOptionResponse((long) i, ordered.get(i).getText())).toList();
        List<AnswerOptionResponse> right = ordered.stream()
            .map(option -> new AnswerOptionResponse(option.getId(), option.getMatchText())).toList();
        yield of(question, left, shuffledDifferently(right));
      }
      case FILL_IN -> of(question, List.of(), List.of());
      case CHOICE -> of(question, question.getOptions().stream()
          .map(AnswerOptionResponse::from).toList(), List.of());
    };
  }

  private static QuestionResponse of(Question question, List<AnswerOptionResponse> options,
      List<AnswerOptionResponse> matches) {
    return new QuestionResponse(question.getId(), question.getText(), question.getPosition(),
        question.getType(), options, matches);
  }

  /** Shows the items in an order other than the authored one, so the layout is not the answer. */
  private static List<AnswerOptionResponse> shuffledDifferently(List<AnswerOptionResponse> authored) {
    List<AnswerOptionResponse> shuffled = new ArrayList<>(authored);
    Collections.shuffle(shuffled);
    if (shuffled.size() > 1 && shuffled.equals(authored)) {
      Collections.rotate(shuffled, 1);
    }
    return shuffled.stream().collect(Collectors.toUnmodifiableList());
  }
}
