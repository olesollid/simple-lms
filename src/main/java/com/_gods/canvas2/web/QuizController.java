package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.grading.QuizGrade;
import com._gods.canvas2.grading.QuizGradeStore;
import com._gods.canvas2.model.Question;
import com._gods.canvas2.service.QuizGradeNotFoundException;
import com._gods.canvas2.service.QuizService;
import com._gods.canvas2.service.QuizSubmissionService;
import com._gods.canvas2.web.dto.QuestionRequest;
import com._gods.canvas2.web.dto.QuestionResponse;
import com._gods.canvas2.web.dto.QuestionWithAnswersResponse;
import com._gods.canvas2.web.dto.QuizSubmissionRequest;

@RestController
@RequestMapping("/api/quizzes/{quizId}")
public class QuizController {

  private final QuizService quizService;
  private final QuizSubmissionService quizSubmissionService;
  private final QuizGradeStore quizGradeStore;

  public QuizController(QuizService quizService, QuizSubmissionService quizSubmissionService,
      QuizGradeStore quizGradeStore) {
    this.quizService = quizService;
    this.quizSubmissionService = quizSubmissionService;
    this.quizGradeStore = quizGradeStore;
  }

  @PostMapping("/questions")
  public ResponseEntity<QuestionWithAnswersResponse> addQuestion(@PathVariable Long quizId,
      @RequestBody QuestionRequest request, Authentication authentication) {
    Question question = quizService.addQuestion(quizId, request, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(QuestionWithAnswersResponse.from(question));
  }

  @GetMapping("/questions")
  public List<QuestionResponse> getQuestions(@PathVariable Long quizId,
      Authentication authentication) {
    return quizService.getQuestions(quizId, authentication.getName()).stream()
        .map(QuestionResponse::from)
        .toList();
  }

  @GetMapping("/questions/with-answers")
  public List<QuestionWithAnswersResponse> getQuestionsWithAnswers(@PathVariable Long quizId,
      Authentication authentication) {
    return quizService.getQuestionsWithAnswers(quizId, authentication.getName()).stream()
        .map(QuestionWithAnswersResponse::from)
        .toList();
  }

  @PostMapping("/submissions")
  public ResponseEntity<Void> submit(@PathVariable Long quizId,
      @RequestBody QuizSubmissionRequest request, Authentication authentication) {
    quizSubmissionService.submit(quizId, request, authentication.getName());
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/grade")
  public QuizGrade getGrade(@PathVariable Long quizId, Authentication authentication) {
    quizService.requirePublishedGrade(quizId, authentication.getName());
    return quizGradeStore.find(quizId, authentication.getName())
        .orElseThrow(() -> new QuizGradeNotFoundException(quizId, authentication.getName()));
  }
}
