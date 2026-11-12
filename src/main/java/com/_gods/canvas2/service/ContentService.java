package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
public class ContentService {

  private final ContentRepository contentRepository;
  private final ModuleRepository moduleRepository;

  public ContentService(ContentRepository contentRepository, ModuleRepository moduleRepository) {
    this.contentRepository = contentRepository;
    this.moduleRepository = moduleRepository;
  }

  public StaticPage createStaticPage(Long moduleId, String title, int position, String body,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    return contentRepository.save(
        new StaticPage(module, title, position, body, visibleFrom, visibleUntil));
  }

  public List<Content> getContentsByModule(Long moduleId) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    return contentRepository.findByModuleOrderByPosition(module);
  }

  public Content getContentById(Long id) {
    return contentRepository.findById(id)
        .orElseThrow(() -> new ContentNotFoundException(id));
  }

  public Content updateContentMetadata(Long id, String title, int position,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil) {
    Content content = getContentById(id);
    content.updateMetadata(title, position, visibleFrom, visibleUntil);
    return contentRepository.save(content);
  }

  public StaticPage updateStaticPageBody(Long id, String body) {
    Content content = getContentById(id);
    if (!(content instanceof StaticPage staticPage)) {
      throw new IllegalArgumentException("Content is not a static page: " + id);
    }
    staticPage.setBody(body);
    return contentRepository.save(staticPage);
  }

  public void deleteContent(Long id) {
    Content content = getContentById(id);
    contentRepository.delete(content);
  }
}
