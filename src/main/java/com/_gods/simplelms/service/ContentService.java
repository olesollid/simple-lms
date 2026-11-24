package com._gods.simplelms.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.CodingExercise;
import com._gods.simplelms.model.Content;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.StaticPage;
import com._gods.simplelms.repository.ContentRepository;
import com._gods.simplelms.repository.ModuleRepository;
import com._gods.simplelms.repository.StudentInteractionRepository;
import com._gods.simplelms.repository.SubmissionRepository;

@Service
@Transactional
public class ContentService {

  private final ContentRepository contentRepository;
  private final ModuleRepository moduleRepository;
  private final EnrollmentService enrollmentService;
  private final ModuleService moduleService;
  private final SubmissionRepository submissionRepository;
  private final StudentInteractionRepository studentInteractionRepository;

  public ContentService(ContentRepository contentRepository, ModuleRepository moduleRepository,
      EnrollmentService enrollmentService, ModuleService moduleService,
      SubmissionRepository submissionRepository,
      StudentInteractionRepository studentInteractionRepository) {
    this.contentRepository = contentRepository;
    this.moduleRepository = moduleRepository;
    this.enrollmentService = enrollmentService;
    this.moduleService = moduleService;
    this.submissionRepository = submissionRepository;
    this.studentInteractionRepository = studentInteractionRepository;
  }

  /**
   * New content goes at the end of its module, so items appear in the order they were added. The
   * {@code position} in create requests is ignored; teachers reorder with {@link #reorderContents}.
   */
  private int nextPosition(Module module) {
    return contentRepository.findByModuleOrderByPosition(module).stream()
        .mapToInt(content -> content.getPosition() + 1).max().orElse(0);
  }

  public StaticPage createStaticPage(Long moduleId, String title, int position, String body,
      LocalDateTime visibleFrom, LocalDateTime visibleUntil, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return contentRepository.save(
        new StaticPage(module, title, nextPosition(module), body, visibleFrom, visibleUntil));
  }

  public Quiz createQuiz(Long moduleId, String title, int position, int maxAttempts,
      LocalDateTime deadline, String requesterEmail) {
    return createQuiz(moduleId, title, position, maxAttempts, deadline, true,
        requesterEmail);
  }

  public Quiz createQuiz(Long moduleId, String title, int position, int maxAttempts,
      LocalDateTime deadline, boolean showResultsImmediately,
      String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    Quiz quiz = new Quiz(module, title, nextPosition(module), maxAttempts, deadline);
    quiz.setShowResultsImmediately(showResultsImmediately);
    return (Quiz) contentRepository.save(quiz);
  }

  public Assignment createAssignment(Long moduleId, String title, int position,
      String instructions, LocalDateTime deadline, boolean lateSubmissionsAccepted,
      boolean groupSubmission, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return (Assignment) contentRepository.save(new Assignment(module, title, nextPosition(module),
        instructions, deadline, lateSubmissionsAccepted, groupSubmission));
  }

  public CodingExercise createCodingExercise(Long moduleId, String title, int position,
      String repositoryUrl, LocalDateTime deadline, boolean lateSubmissionsAccepted,
      boolean groupSubmission, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    return (CodingExercise) contentRepository.save(new CodingExercise(module, title, nextPosition(module),
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
    LocalDateTime now = LocalDateTime.now(HandInPolicy.ZONE);
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
        || !content.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE))) {
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

  /**
   * Teacher-only: puts a module's content in the given order. The list must contain each of the
   * module's content items exactly once.
   */
  public List<Content> reorderContents(Long moduleId, List<Long> contentIds, String requesterEmail) {
    Module module = moduleRepository.findById(moduleId)
        .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    requireTeacher(module.getCourse().getId(), requesterEmail);
    List<Content> current = contentRepository.findByModuleOrderByPosition(module);
    ModuleService.requireSameIds(current.stream().map(Content::getId).toList(), contentIds, "content items");
    for (Content content : current) {
      content.moveTo(contentIds.indexOf(content.getId()));
    }
    contentRepository.saveAll(current);
    return contentRepository.findByModuleOrderByPosition(module);
  }

  /**
   * Teacher-only: changes the main text of a content item -- a page's body, an assignment's
   * instructions or a coding exercise's repository URL. Quizzes are edited question by question.
   */
  public Content updateText(Long id, String text, String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    switch (content) {
      case StaticPage page -> page.setBody(text);
      case Assignment assignment -> assignment.updateInstructions(text);
      case CodingExercise exercise -> exercise.updateRepositoryUrl(text);
      default -> throw new IllegalArgumentException("This content has no text to edit");
    }
    return contentRepository.save(content);
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

  public Content updateSubmissionPolicy(Long id, LocalDateTime deadline, boolean acceptsLate,
      String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    switch (content) {
      case Assignment assignment -> assignment.updateSubmissionPolicy(deadline, acceptsLate);
      case CodingExercise exercise -> exercise.updateSubmissionPolicy(deadline, acceptsLate);
      // A quiz simply closes at its deadline; there are no late attempts to accept.
      case Quiz quiz -> quiz.updateDeadline(deadline);
      default -> throw new IllegalArgumentException("Only assignments, coding exercises and quizzes have deadlines");
    }
    return contentRepository.save(content);
  }

  /** Deleting content also removes submissions and interactions tied to it, which would
   * otherwise block the delete with a foreign-key violation. */
  public void deleteContent(Long id, String requesterEmail) {
    Content content = getContentById(id);
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    submissionRepository.deleteAll(submissionRepository.findByContent(content));
    studentInteractionRepository.deleteAll(studentInteractionRepository.findByContent(content));
    contentRepository.delete(content);
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!isTeacher(courseId, requesterEmail)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
