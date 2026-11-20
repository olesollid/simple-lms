package com._gods.canvas2.web;

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

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.StudentInteractionService;
import com._gods.canvas2.web.dto.AssignmentRequest;
import com._gods.canvas2.web.dto.CodingExerciseRequest;
import com._gods.canvas2.web.dto.ContentMetadataRequest;
import com._gods.canvas2.web.dto.ContentResponse;
import com._gods.canvas2.web.dto.QuizRequest;
import com._gods.canvas2.web.dto.StaticPageBodyRequest;
import com._gods.canvas2.web.dto.StaticPageRequest;
import com._gods.canvas2.web.dto.StudentInteractionResponse;

@RestController
@RequestMapping("/api")
public class ContentController {

  private final ContentService contentService;
  private final StudentInteractionService studentInteractionService;

  public ContentController(ContentService contentService,
      StudentInteractionService studentInteractionService) {
    this.contentService = contentService;
    this.studentInteractionService = studentInteractionService;
  }

  @PostMapping("/modules/{moduleId}/pages")
  public ResponseEntity<ContentResponse> createStaticPage(@PathVariable Long moduleId,
      @RequestBody StaticPageRequest request, Authentication authentication) {
    StaticPage page = contentService.createStaticPage(moduleId, request.title(),
        request.position(), request.body(), request.visibleFrom(), request.visibleUntil(),
        authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(page));
  }

  @PostMapping("/modules/{moduleId}/quizzes")
  public ResponseEntity<ContentResponse> createQuiz(@PathVariable Long moduleId,
      @RequestBody QuizRequest request, Authentication authentication) {
    Quiz quiz = contentService.createQuiz(moduleId, request.title(), request.position(),
        request.maxAttempts(), request.showHints(), request.deadline(),
        authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(quiz));
  }

  @PostMapping("/modules/{moduleId}/assignments")
  public ResponseEntity<ContentResponse> createAssignment(@PathVariable Long moduleId,
      @RequestBody AssignmentRequest request, Authentication authentication) {
    Assignment assignment = contentService.createAssignment(moduleId, request.title(),
        request.position(), request.instructions(), request.deadline(),
        request.lateSubmissionsAccepted(), request.groupSubmission(), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(assignment));
  }

  @PostMapping("/modules/{moduleId}/coding-exercises")
  public ResponseEntity<ContentResponse> createCodingExercise(@PathVariable Long moduleId,
      @RequestBody CodingExerciseRequest request, Authentication authentication) {
    CodingExercise codingExercise = contentService.createCodingExercise(moduleId, request.title(),
        request.position(), request.repositoryUrl(), request.deadline(),
        request.lateSubmissionsAccepted(), request.groupSubmission(), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(codingExercise));
  }

  @GetMapping("/modules/{moduleId}/contents")
  public List<ContentResponse> getContentsByModule(@PathVariable Long moduleId,
      Authentication authentication) {
    return contentService.getContentsByModule(moduleId, authentication.getName()).stream()
        .map(ContentResponse::from)
        .toList();
  }

  @GetMapping("/contents/{contentId}")
  public ContentResponse getContentById(@PathVariable Long contentId,
      Authentication authentication) {
    return ContentResponse.from(contentService.getContentById(contentId, authentication.getName()));
  }

  @PutMapping("/contents/{contentId}")
  public ContentResponse updateContent(@PathVariable Long contentId,
      @RequestBody ContentMetadataRequest request, Authentication authentication) {
    Content content = contentService.updateContentMetadata(contentId, request.title(),
        request.position(), request.visibleFrom(), request.visibleUntil(),
        authentication.getName());
    return ContentResponse.from(content);
  }

  @PutMapping("/contents/{contentId}/body")
  public ContentResponse updateStaticPageBody(@PathVariable Long contentId,
      @RequestBody StaticPageBodyRequest request, Authentication authentication) {
    StaticPage page = contentService.updateStaticPageBody(contentId, request.body(),
        authentication.getName());
    return ContentResponse.from(page);
  }

  @DeleteMapping("/contents/{contentId}")
  public ResponseEntity<Void> deleteContent(@PathVariable Long contentId,
      Authentication authentication) {
    contentService.deleteContent(contentId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/contents/{contentId}/interactions")
  public StudentInteractionResponse markCompleted(@PathVariable Long contentId,
      Authentication authentication) {
    StudentInteraction interaction =
        studentInteractionService.markCompleted(contentId, authentication.getName());
    return StudentInteractionResponse.from(interaction);
  }
}
