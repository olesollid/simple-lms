package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
@Transactional
public class ContentService {

  private final ContentRepository contentRepository;
  private final ModuleRepository moduleRepository;
  private final EnrollmentService enrollmentService;

  public ContentService(ContentRepository contentRepository, ModuleRepository moduleRepository,
      EnrollmentService enrollmentService) {
    this.contentRepository = contentRepository;
    this.moduleRepository = moduleRepository;
    this.enrollmentService = enrollmentService;
  }

  public StaticPage createStaticPage(Long moduleId, String title, int position, String body,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return contentRepository.save(
        new StaticPage(module, title, position, body, visibleFrom, visibleUntil));
  }

  @Transactional(readOnly = true)
  public List<Content> getContentsByModule(Long moduleId, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    enrollmentService.requireEnrolled(module.getCourse().getId(), requesterEmail);
    return contentRepository.findByModuleOrderByPosition(module);
  }

  @Transactional(readOnly = true)
  public Content getContentById(Long id, String requesterEmail) {
    Content content = getContentById(id);
    enrollmentService.requireEnrolled(content.getModule().getCourse().getId(), requesterEmail);
    return content;
  }

  private Content getContentById(Long id) {
    return contentRepository.findById(id)
        .orElseThrow(() -> new ContentNotFoundException(id));
  }

  public Content updateContentMetadata(Long id, String title, int position,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil, String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    content.updateMetadata(title, position, visibleFrom, visibleUntil);
    return contentRepository.save(content);
  }

  public StaticPage updateStaticPageBody(Long id, String body, String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    if (!(content instanceof StaticPage staticPage)) {
      throw new IllegalArgumentException("Content is not a static page: " + id);
    }
    staticPage.setBody(body);
    return contentRepository.save(staticPage);
  }

  public void deleteContent(Long id, String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    contentRepository.delete(content);
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
