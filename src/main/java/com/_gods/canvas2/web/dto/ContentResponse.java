package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.StaticPage;

public record ContentResponse(Long id, Long moduleId, String type, String title, int position,
    LocalDateTime visibleFrom, LocalDateTime visibleUntil, String body) {

  public static ContentResponse from(Content content) {
    String body = content instanceof StaticPage staticPage ? staticPage.getBody() : null;
    return new ContentResponse(content.getId(), content.getModule().getId(),
        content.getClass().getSimpleName(), content.getTitle(), content.getPosition(),
        content.getVisibleFrom(), content.getVisibleUntil(), body);
  }
}
