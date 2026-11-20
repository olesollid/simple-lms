package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.model.Submission;
import com._gods.canvas2.service.SubmissionService;
import com._gods.canvas2.web.dto.GradeRequest;
import com._gods.canvas2.web.dto.SubmissionRequest;
import com._gods.canvas2.web.dto.SubmissionResponse;

@RestController
@RequestMapping("/api")
public class SubmissionController {

  private final SubmissionService submissionService;

  public SubmissionController(SubmissionService submissionService) {
    this.submissionService = submissionService;
  }

  @PostMapping("/contents/{contentId}/submissions")
  public ResponseEntity<SubmissionResponse> submit(@PathVariable Long contentId,
      @RequestBody SubmissionRequest request, Authentication authentication) {
    Submission submission = submissionService.submit(contentId, request.text(),
        authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.forStudent(submission));
  }

  @GetMapping("/contents/{contentId}/submissions")
  public List<SubmissionResponse> getSubmissionsByContent(@PathVariable Long contentId,
      Authentication authentication) {
    return submissionService.getSubmissionsByContent(contentId, authentication.getName()).stream()
        .map(SubmissionResponse::from)
        .toList();
  }

  @GetMapping("/contents/{contentId}/submissions/mine")
  public SubmissionResponse getOwnSubmission(@PathVariable Long contentId,
      Authentication authentication) {
    return SubmissionResponse.forStudent(
        submissionService.getOwnSubmission(contentId, authentication.getName()));
  }

  @GetMapping("/contents/{contentId}/grades/publication")
  public boolean gradesPublished(@PathVariable Long contentId, Authentication authentication) {
    return submissionService.gradesPublished(contentId, authentication.getName());
  }

  @PutMapping("/contents/{contentId}/grades/publication")
  public boolean publishGrades(@PathVariable Long contentId, Authentication authentication) {
    return submissionService.publishGrades(contentId, authentication.getName());
  }

  @PutMapping("/submissions/{submissionId}/grade")
  public SubmissionResponse grade(@PathVariable Long submissionId,
      @RequestBody GradeRequest request, Authentication authentication) {
    Submission submission = submissionService.grade(submissionId, request.grade(),
        request.feedback(), authentication.getName());
    return SubmissionResponse.from(submission);
  }
}
