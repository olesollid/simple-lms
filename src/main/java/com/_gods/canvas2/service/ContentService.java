package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Quiz;
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
  private final ModuleService moduleService;

  public ContentService(ContentRepository contentRepository, ModuleRepository moduleRepository,
      EnrollmentService enrollmentService, ModuleService moduleService) {
    this.contentRepository = contentRepository;
    this.moduleRepository = moduleRepository;
    this.enrollmentService = enrollmentService;
    this.moduleService = moduleService;
  }

  public StaticPage createStaticPage(Long moduleId, String title, int position, String body,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return contentRepository.save(
        new StaticPage(module, title, position, body, visibleFrom, visibleUntil));
  }

  public Quiz createQuiz(Long moduleId, String title, int position, int maxAttempts,
      boolean showHints, LocalDateTime deadline, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return (Quiz) contentRepository.save(new Quiz(module, title, position, maxAttempts, showHints, deadline));
  }

  public Assignment createAssignment(Long moduleId, String title, int position,
      String instructions, LocalDateTime deadline, boolean lateSubmissionsAccepted,
      boolean groupSubmission, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return (Assignment) contentRepository.save(new Assignment(module, title, position,
        instructions, deadline, lateSubmissionsAccepted, groupSubmission));
  }

  public CodingExercise createCodingExercise(Long moduleId, String title, int position,
      String repositoryUrl, LocalDateTime deadline, boolean lateSubmissionsAccepted,
      boolean groupSubmission, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return (CodingExercise) contentRepository.save(new CodingExercise(module, title, position,
        repositoryUrl, deadline, lateSubmissionsAccepted, groupSubmission));
  }

  @Transactional(readOnly = true)
  public List<Content> getContentsByModule(Long moduleId, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    enrollmentService.requireEnrolled(module.getCourse().getId(), requesterEmail);
    List<Content> contents = contentRepository.findByModuleOrderByPosition(module);
    if (isTeacher(module.getCourse().getId(), requesterEmail)) {
      return contents;
    }
    if (moduleService.isLocked(module, requesterEmail)) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    return contents.stream().filter(content -> content.isVisibleAt(now)).toList();
  }

  @Transactional(readOnly = true)
  public Content getContentById(Long id, String requesterEmail) {
    Content content = getContentById(id);
    Long courseId = content.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    if (isTeacher(courseId, requesterEmail)) {
      return content;
    }
    if (moduleService.isLocked(content.getModule(), requesterEmail)
        || !content.isVisibleAt(LocalDateTime.now())) {
      throw new ContentNotFoundException(id);
    }
    return content;
  }

  private boolean isTeacher(Long courseId, String requesterEmail) {
    return enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER);
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
    if (!isTeacher(courseId, requesterEmail)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
