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

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.CodingExercise;
import com._gods.simplelms.model.Content;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.StaticPage;
import com._gods.simplelms.model.StudentInteraction;
import com._gods.simplelms.service.ContentService;
import com._gods.simplelms.service.StudentInteractionService;
import com._gods.simplelms.web.dto.AssignmentRequest;
import com._gods.simplelms.web.dto.CodingExerciseRequest;
import com._gods.simplelms.web.dto.ContentMetadataRequest;
import com._gods.simplelms.web.dto.ContentResponse;
import com._gods.simplelms.web.dto.ContentTextRequest;
import com._gods.simplelms.web.dto.OrderRequest;
import com._gods.simplelms.web.dto.QuizRequest;
import com._gods.simplelms.web.dto.StaticPageBodyRequest;
import com._gods.simplelms.web.dto.StaticPageRequest;
import com._gods.simplelms.web.dto.StudentInteractionResponse;

@RestController
@RequestMapping("/api")
public class ContentController {

  @PutMapping("/contents/{contentId}/submission-policy")
  public ContentResponse updateSubmissionPolicy(@PathVariable Long contentId,
      @RequestBody com._gods.simplelms.web.dto.SubmissionPolicyRequest request,
      Authentication authentication) {
    return ContentResponse.from(contentService.updateSubmissionPolicy(contentId, request.deadline(),
        request.lateSubmissionsAccepted(), authentication.getName()));
  }

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
        request.maxAttempts(), request.deadline(),
        !Boolean.FALSE.equals(request.showResultsImmediately()), authentication.getName());
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

  /** Teacher-only: a page body, assignment instructions or coding exercise repository URL. */
  @PutMapping("/contents/{contentId}/text")
  public ContentResponse updateText(@PathVariable Long contentId,
      @RequestBody ContentTextRequest request, Authentication authentication) {
    return ContentResponse.from(
        contentService.updateText(contentId, request.text(), authentication.getName()));
  }

  /** Teacher-only: new order for a module's content. */
  @PutMapping("/modules/{moduleId}/contents/order")
  public List<ContentResponse> reorderContents(@PathVariable Long moduleId,
      @RequestBody OrderRequest request, Authentication authentication) {
    return contentService.reorderContents(moduleId, request.ids(), authentication.getName()).stream()
        .map(ContentResponse::from)
        .toList();
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

  /** The requester's own completed content in a course (for progress checkmarks). */
  @GetMapping("/courses/{courseId}/interactions/mine")
  public List<StudentInteractionResponse> getOwnCompletions(@PathVariable Long courseId,
      Authentication authentication) {
    return studentInteractionService.getOwnCompletions(courseId, authentication.getName()).stream()
        .map(StudentInteractionResponse::from)
        .toList();
  }

  @PostMapping("/contents/{contentId}/interactions")
  public StudentInteractionResponse markCompleted(@PathVariable Long contentId,
      Authentication authentication) {
    StudentInteraction interaction =
        studentInteractionService.markCompleted(contentId, authentication.getName());
    return StudentInteractionResponse.from(interaction);
  }
}
