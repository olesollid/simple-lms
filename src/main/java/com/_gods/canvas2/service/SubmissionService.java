package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.HandInPolicy;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.SubmissionFile;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.SubmissionRepository;

/**
 * Handles student hand-ins for {@link com._gods.canvas2.model.Assignment}/
 * {@link com._gods.canvas2.model.CodingExercise} content and their manual, teacher-driven
 * grading. Unlike quiz submissions (auto-graded, never persisted as their own entity), these are
 * always persisted so a teacher can review and grade them later with written feedback. Content
 * flagged as group-submittable gets one shared {@link Submission} per group instead of one per
 * student: see {@link #submit} for how the two paths are chosen.
 */
@Service
@Transactional
public class SubmissionService {

  private final SubmissionRepository submissionRepository;
  private final ContentRepository contentRepository;
  private final UserService userService;
  private final EnrollmentService enrollmentService;
  private final ModuleService moduleService;
  private final StudentInteractionService studentInteractionService;
  private final GroupService groupService;

  public SubmissionService(SubmissionRepository submissionRepository,
      ContentRepository contentRepository, UserService userService,
      EnrollmentService enrollmentService, ModuleService moduleService,
      StudentInteractionService studentInteractionService, GroupService groupService) {
    this.submissionRepository = submissionRepository;
    this.contentRepository = contentRepository;
    this.userService = userService;
    this.enrollmentService = enrollmentService;
    this.moduleService = moduleService;
    this.studentInteractionService = studentInteractionService;
    this.groupService = groupService;
  }

  /**
   * Submits (or resubmits, if ungraded) a hand-in for content that must still be visible to the
   * requester -- not in a locked module, not outside its visibility window -- matching the same
   * gate enforced on the read path and on {@link StudentInteractionService#markCompleted}.
   * Submitting also marks the content completed, the same unambiguous "done" signal used for quiz
   * submission. When the content is group-submittable, a student requester must belong to a
   * group in the course, and the submission is shared: whichever member submits most recently
   * overwrites the previous text, and that single submission is what the teacher grades. A
   * teacher is never a group member, so a teacher's own submission is always recorded
   * individually, same as for non-group content.
   */
  public Submission submit(Long contentId, String text, String requesterEmail) {
    return submit(contentId, text, null, requesterEmail);
  }

  public Submission submit(Long contentId, String text, SubmissionFile file, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    if (!(content instanceof Assignment) && !(content instanceof CodingExercise)) {
      throw new IllegalArgumentException("Only assignments and coding exercises accept hand-ins");
    }
    Long courseId = content.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    boolean isTeacher = enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER);
    if (!isTeacher && (moduleService.isLocked(content.getModule(), requesterEmail)
        || !content.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE)))) {
      throw new ContentNotFoundException(contentId);
    }
    LocalDateTime submittedAt = LocalDateTime.now(HandInPolicy.ZONE);
    if (HandInPolicy.isLate(content, submittedAt) && !HandInPolicy.acceptsLate(content)) {
      throw new ForbiddenException("The deadline has passed. Late submissions are not accepted.");
    }
    User submittedBy = userService.getUserByEmail(requesterEmail);

    if (!isTeacher && isGroupSubmission(content)) {
      Group group = groupService.getOwnGroup(courseId, requesterEmail);
      Submission saved;
      try {
        saved = saveGroupSubmission(group, submittedBy, content, text, file);
      } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
        saved = updateExistingGroupSubmission(group, submittedBy, content, text, file);
      }
      saved.recordSubmissionTime(submittedAt);
      studentInteractionService.markCompletedForGroup(content, group);
      return saved;
    }

    Submission saved;
    try {
      saved = saveSubmission(submittedBy, content, text, file);
    } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
      saved = updateExistingSubmission(submittedBy, content, text, file);
    }
    saved.recordSubmissionTime(submittedAt);
    studentInteractionService.markCompleted(contentId, requesterEmail);
    return saved;
  }

  private static boolean isGroupSubmission(Content content) {
    return switch (content) {
      case Assignment assignment -> assignment.isGroupSubmission();
      case CodingExercise codingExercise -> codingExercise.isGroupSubmission();
      default -> false;
    };
  }

  /**
   * Runs in its own transaction: a unique (user, content) constraint means a concurrent duplicate
   * call can make a fresh insert fail, which marks the surrounding transaction for rollback.
   * Keeping this attempt in its own transaction lets the fallback in
   * {@link #updateExistingSubmission} still run cleanly in the caller's transaction afterward.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected Submission saveSubmission(User submitter, Content content, String text, SubmissionFile file) {
    Submission existing = submissionRepository.findByContentAndSubmitter(content, submitter)
        .orElse(null);
    if (existing != null) {
      if (existing.isGraded()) {
        throw new ConflictException(
            "Cannot resubmit content " + content.getId() + ": it has already been graded");
      }
      existing.resubmit(text, submitter, file);
      return submissionRepository.save(existing);
    }
    return submissionRepository.save(new Submission(submitter, content, text, file));
  }

  private Submission updateExistingSubmission(User submitter, Content content, String text, SubmissionFile file) {
    Submission existing = submissionRepository.findByContentAndSubmitter(content, submitter)
        .orElseThrow(() -> new IllegalStateException(
            "Expected a concurrently-inserted Submission for user " + submitter.getEmail()
                + " and content " + content.getId() + " but found none"));
    if (existing.isGraded()) {
      throw new ConflictException(
          "Cannot resubmit content " + content.getId() + ": it has already been graded");
    }
    existing.resubmit(text, submitter, file);
    return submissionRepository.save(existing);
  }

  /** Same race-safety strategy as {@link #saveSubmission}, keyed by (group, content) instead. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected Submission saveGroupSubmission(Group group, User submittedBy, Content content,
      String text, SubmissionFile file) {
    Submission existing = submissionRepository.findByContentAndGroup(content, group).orElse(null);
    if (existing != null) {
      if (existing.isGraded()) {
        throw new ConflictException(
            "Cannot resubmit content " + content.getId() + ": it has already been graded");
      }
      existing.resubmit(text, submittedBy, file);
      return submissionRepository.save(existing);
    }
    return submissionRepository.save(new Submission(group, submittedBy, content, text, file));
  }

  private Submission updateExistingGroupSubmission(Group group, User submittedBy, Content content,
      String text, SubmissionFile file) {
    Submission existing = submissionRepository.findByContentAndGroup(content, group)
        .orElseThrow(() -> new IllegalStateException(
            "Expected a concurrently-inserted Submission for group " + group.getId()
                + " and content " + content.getId() + " but found none"));
    if (existing.isGraded()) {
      throw new ConflictException(
          "Cannot resubmit content " + content.getId() + ": it has already been graded");
    }
    existing.resubmit(text, submittedBy, file);
    return submissionRepository.save(existing);
  }

  @Transactional(readOnly = true)
  public List<Submission> getSubmissionsByContent(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    return submissionRepository.findByContent(content);
  }

  /**
   * The requester's own submission: their individual submission, or -- for group-submittable
   * content, when the requester is a student -- their group's shared submission. A teacher is
   * never a group member, so a teacher's own submission (if any) is always looked up
   * individually, same as for non-group content.
   */
  @Transactional(readOnly = true)
  public Submission getOwnSubmission(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    Long courseId = content.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    boolean isTeacher = enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER);

    if (!isTeacher && isGroupSubmission(content)) {
      Group group = groupService.getOwnGroup(courseId, requesterEmail);
      return submissionRepository.findByContentAndGroup(content, group)
          .orElseThrow(() -> new NotFoundException(
              "No submission by group " + group.getId() + " for content " + contentId));
    }

    User submitter = userService.getUserByEmail(requesterEmail);
    return submissionRepository.findByContentAndSubmitter(content, submitter)
        .orElseThrow(() -> new NotFoundException(
            "No submission by " + requesterEmail + " for content " + contentId));
  }

  public Submission grade(Long submissionId, double grade, String feedback,
      String requesterEmail) {
    Submission submission = submissionRepository.findById(submissionId)
        .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));
    requireTeacher(submission.getContent().getModule().getCourse().getId(), requesterEmail);
    submission.grade(grade, feedback);
    return submissionRepository.save(submission);
  }

  @Transactional(readOnly = true)
  public boolean gradesPublished(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    return content.isGradesPublished();
  }

  public boolean publishGrades(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    requireTeacher(content.getModule().getCourse().getId(), requesterEmail);
    // Publishing can't be undone, so a draft quiz must not end up with "results published"
    // before anyone can even take it.
    if (content instanceof Quiz quiz && !quiz.isPublished()) {
      throw new ConflictException("Publish the quiz before publishing its results.");
    }
    content.publishGrades();
    contentRepository.save(content);
    return true;
  }

  @Transactional(readOnly = true)
  public SubmissionFile downloadFile(Long submissionId, String requesterEmail) {
    Submission submission = submissionRepository.findById(submissionId)
        .orElseThrow(() -> new NotFoundException("Submission not found"));
    Long courseId = submission.getContent().getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      Submission own = getOwnSubmission(submission.getContent().getId(), requesterEmail);
      if (!own.getId().equals(submissionId)) {
        throw new ForbiddenException("You may only download your own or your group's file");
      }
    }
    if (submission.getFile() == null) throw new NotFoundException("No file attached");
    return submission.getFile();
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
