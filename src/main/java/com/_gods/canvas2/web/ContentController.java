package com._gods.canvas2.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.web.dto.ContentMetadataRequest;
import com._gods.canvas2.web.dto.ContentResponse;
import com._gods.canvas2.web.dto.StaticPageBodyRequest;
import com._gods.canvas2.web.dto.StaticPageRequest;

@RestController
@RequestMapping("/api")
public class ContentController {

  private final ContentService contentService;

  public ContentController(ContentService contentService) {
    this.contentService = contentService;
  }

  @PostMapping("/modules/{moduleId}/pages")
  public ResponseEntity<ContentResponse> createStaticPage(@PathVariable Long moduleId,
      @RequestBody StaticPageRequest request) {
    StaticPage page = contentService.createStaticPage(moduleId, request.title(),
        request.position(), request.body(), request.visibleFrom(), request.visibleUntil());
    return ResponseEntity.status(HttpStatus.CREATED).body(ContentResponse.from(page));
  }

  @GetMapping("/modules/{moduleId}/contents")
  public List<ContentResponse> getContentsByModule(@PathVariable Long moduleId) {
    return contentService.getContentsByModule(moduleId).stream()
        .map(ContentResponse::from)
        .toList();
  }

  @GetMapping("/contents/{contentId}")
  public ContentResponse getContentById(@PathVariable Long contentId) {
    return ContentResponse.from(contentService.getContentById(contentId));
  }

  @PutMapping("/contents/{contentId}")
  public ContentResponse updateContent(@PathVariable Long contentId,
      @RequestBody ContentMetadataRequest request) {
    Content content = contentService.updateContentMetadata(contentId, request.title(),
        request.position(), request.visibleFrom(), request.visibleUntil());
    return ContentResponse.from(content);
  }

  @PutMapping("/contents/{contentId}/body")
  public ContentResponse updateStaticPageBody(@PathVariable Long contentId,
      @RequestBody StaticPageBodyRequest request) {
    StaticPage page = contentService.updateStaticPageBody(contentId, request.body());
    return ContentResponse.from(page);
  }

  @DeleteMapping("/contents/{contentId}")
  public ResponseEntity<Void> deleteContent(@PathVariable Long contentId) {
    contentService.deleteContent(contentId);
    return ResponseEntity.noContent().build();
  }
}
