package com._gods.simplelms.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.model.Question;
import com._gods.simplelms.service.QuizGradeNotFoundException;
import com._gods.simplelms.service.QuizService;
import com._gods.simplelms.service.QuizSubmissionService;
import com._gods.simplelms.web.dto.ContentResponse;
import com._gods.simplelms.web.dto.QuestionRequest;
import com._gods.simplelms.web.dto.QuestionResponse;
import com._gods.simplelms.web.dto.QuestionWithAnswersResponse;
import com._gods.simplelms.web.dto.QuizAttemptsResponse;
import com._gods.simplelms.web.dto.QuizStudentResultResponse;
import com._gods.simplelms.web.dto.QuizSubmissionRequest;
import com._gods.simplelms.web.dto.QuizSubmissionResponse;
import com._gods.simplelms.web.dto.ResultVisibilityRequest;

@RestController
@RequestMapping("/api/quizzes/{quizId}")
public class QuizController {

  private final QuizService quizService;
  private final QuizSubmissionService quizSubmissionService;

  public QuizController(QuizService quizService, QuizSubmissionService quizSubmissionService) {
    this.quizService = quizService;
    this.quizSubmissionService = quizSubmissionService;
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

  @PutMapping("/questions/{questionId}")
  public QuestionWithAnswersResponse updateQuestion(@PathVariable Long quizId,
      @PathVariable Long questionId, @RequestBody QuestionRequest request,
      Authentication authentication) {
    return QuestionWithAnswersResponse.from(
        quizService.updateQuestion(quizId, questionId, request, authentication.getName()));
  }

  @DeleteMapping("/questions/{questionId}")
  public ResponseEntity<Void> deleteQuestion(@PathVariable Long quizId,
      @PathVariable Long questionId, Authentication authentication) {
    quizService.deleteQuestion(quizId, questionId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  /** Teacher-only: show results as soon as attempts are graded, or only once published. */
  @PutMapping("/result-visibility")
  public ContentResponse setResultVisibility(@PathVariable Long quizId,
      @RequestBody ResultVisibilityRequest request, Authentication authentication) {
    return ContentResponse.from(quizService.setShowResultsImmediately(quizId,
        request.showResultsImmediately(), authentication.getName()));
  }

  @PutMapping("/publication")
  public ContentResponse publish(@PathVariable Long quizId, Authentication authentication) {
    return ContentResponse.from(quizService.publishQuiz(quizId, authentication.getName()));
  }

  public record Settings(Integer maxAttempts) {
  }

  /**
   * Teacher-only: the maximum number of attempts ({@code 0} = unlimited), changeable at any time.
   * The deadline is changed via the submission policy and result visibility via
   * {@code /result-visibility}.
   */
  @PutMapping("/settings")
  public ContentResponse updateSettings(@PathVariable Long quizId, @RequestBody Settings request,
      Authentication authentication) {
    if (request.maxAttempts() == null) {
      throw new IllegalArgumentException("maxAttempts is required");
    }
    return ContentResponse.from(
        quizService.updateMaxAttempts(quizId, request.maxAttempts(), authentication.getName()));
  }

  /** Grading is asynchronous: poll {@code GET /attempts/mine} for the result of this attempt. */
  @PostMapping("/submissions")
  public ResponseEntity<QuizSubmissionResponse> submit(@PathVariable Long quizId,
      @RequestBody QuizSubmissionRequest request, Authentication authentication) {
    int attempt = quizSubmissionService.submit(quizId, request, authentication.getName());
    return ResponseEntity.accepted().body(new QuizSubmissionResponse(attempt));
  }

  @GetMapping("/attempts/mine")
  public QuizAttemptsResponse getOwnAttempts(@PathVariable Long quizId,
      Authentication authentication) {
    return quizSubmissionService.getOwnAttempts(quizId, authentication.getName());
  }

  /** Teacher-only: each student's attempts and best grade. */
  @GetMapping("/results")
  public List<QuizStudentResultResponse> getResults(@PathVariable Long quizId,
      Authentication authentication) {
    return quizSubmissionService.getResults(quizId, authentication.getName());
  }

  /** The requester's best graded attempt -- the grade that counts. 404 until it is graded and visible to students. */
  @GetMapping("/grade")
  public QuizGrade getGrade(@PathVariable Long quizId, Authentication authentication) {
    QuizAttemptsResponse attempts =
        quizSubmissionService.getOwnAttempts(quizId, authentication.getName());
    if (attempts.best() == null) {
      throw new QuizGradeNotFoundException(quizId, authentication.getName());
    }
    return attempts.best();
  }
}
