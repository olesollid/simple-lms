package com._gods.simplelms.web;

import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com._gods.simplelms.model.Submission;
import com._gods.simplelms.model.SubmissionFile;
import com._gods.simplelms.service.SubmissionService;
import com._gods.simplelms.web.dto.GradeRequest;
import com._gods.simplelms.web.dto.SubmissionRequest;
import com._gods.simplelms.web.dto.SubmissionResponse;

import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SubmissionController {

  private final SubmissionService submissionService;

  public SubmissionController(SubmissionService submissionService) {
    this.submissionService = submissionService;
  }

  @PostMapping(value = "/contents/{contentId}/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SubmissionResponse> submit(@PathVariable Long contentId,
      @RequestBody SubmissionRequest request, Authentication authentication) {
    Submission submission = submissionService.submit(contentId, request.text(),
        authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.forStudent(submission));
  }

  @PostMapping(value = "/contents/{contentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SubmissionResponse> upload(@PathVariable Long contentId,
      @RequestParam(defaultValue = "") String text, @RequestParam MultipartFile file,
      Authentication authentication) throws IOException {
    if (file.getSize() > SubmissionFile.MAX_BYTES) {
      throw new org.springframework.web.multipart.MaxUploadSizeExceededException(SubmissionFile.MAX_BYTES);
    }
    SubmissionFile attachment = new SubmissionFile(file.getOriginalFilename(), file.getBytes());
    Submission saved = submissionService.submit(contentId, text, attachment, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.forStudent(saved));
  }

  @GetMapping("/submissions/{submissionId}/file")
  public ResponseEntity<byte[]> download(@PathVariable Long submissionId, Authentication authentication) {
    SubmissionFile file = submissionService.downloadFile(submissionId, authentication.getName());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getContentType()))
        .contentLength(file.getSize())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(file.getFilename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.getData());
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
