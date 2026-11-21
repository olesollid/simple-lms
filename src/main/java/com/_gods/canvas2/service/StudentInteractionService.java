package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.HandInPolicy;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.GroupMembership;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.GroupMembershipRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;

/**
 * Tracks which content items a student has completed ({@link StudentInteraction}), used to gate
 * module visibility: a module unlocks once every content item in each module it depends on is
 * completed by that student (draft quizzes don't count). The completion rules per content type:
 * <ul>
 *   <li><b>Page:</b> the student marks it completed themselves ({@link #markCompleted}).</li>
 *   <li><b>Assignment / coding exercise:</b> completed when submitted. For a group submission it
 *       is completed for every member of the group ({@link #markCompletedForGroup}).</li>
 *   <li><b>Quiz:</b> completed once an attempt has been graded (see
 *       {@code SubmissionGradingListener}), whatever the score.</li>
 * </ul>
 */
@Service
@Transactional
public class StudentInteractionService {

  private final StudentInteractionRepository studentInteractionRepository;
  private final ContentRepository contentRepository;
  private final UserService userService;
  private final EnrollmentService enrollmentService;
  private final GroupMembershipRepository groupMembershipRepository;

  public StudentInteractionService(StudentInteractionRepository studentInteractionRepository,
      ContentRepository contentRepository, UserService userService,
      EnrollmentService enrollmentService, GroupMembershipRepository groupMembershipRepository) {
    this.studentInteractionRepository = studentInteractionRepository;
    this.contentRepository = contentRepository;
    this.userService = userService;
    this.enrollmentService = enrollmentService;
    this.groupMembershipRepository = groupMembershipRepository;
  }

  /**
   * A group hands in one submission on behalf of all its members, so the content counts as
   * completed for every member -- otherwise only the member who happened to press submit could
   * unlock the next module. Callers have already checked that the submitter may submit.
   */
  public void markCompletedForGroup(Content content, Group group) {
    for (GroupMembership membership : groupMembershipRepository.findByGroup(group)) {
      User member = membership.getUser();
      StudentInteraction interaction = studentInteractionRepository
          .findByUserAndContent(member, content)
          .orElseGet(() -> new StudentInteraction(member, content));
      interaction.markCompleted();
      studentInteractionRepository.save(interaction);
    }
  }

  /** The requester's own completed content items in a course, so the UI can show progress. */
  @Transactional(readOnly = true)
  public List<StudentInteraction> getOwnCompletions(Long courseId, String requesterEmail) {
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    User user = userService.getUserByEmail(requesterEmail);
    return studentInteractionRepository.findByUser(user).stream()
        .filter(interaction -> interaction.getCompletedAt() != null)
        .filter(interaction -> interaction.getContent().getModule().getCourse().getId().equals(courseId))
        .toList();
  }

  /**
   * Any enrolled member may mark content as completed for themselves, as long as a student could
   * actually see that content: it must not be in a module still locked by an unmet prerequisite,
   * nor outside its own visibility window. Without this check a student could mark content
   * "completed" by content id alone, bypassing the same gate enforced on the read path and
   * unlocking further modules from content they were never shown. Teachers bypass both checks,
   * matching how they bypass them when reading content.
   */
  public StudentInteraction markCompleted(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    Long courseId = content.getModule().getCourse().getId();
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    boolean isTeacher = enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER);
    if (!isTeacher && (isLocked(content.getModule(), requesterEmail)
        || !content.isVisibleAt(LocalDateTime.now(HandInPolicy.ZONE)))) {
      throw new ContentNotFoundException(contentId);
    }
    User user = userService.getUserByEmail(requesterEmail);

    try {
      return saveCompletion(user, content);
    } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
      return updateExistingCompletion(user, content);
    }
  }

  /**
   * Runs in its own transaction: a unique (user, content) constraint means a concurrent duplicate
   * call can make this insert fail, which marks the surrounding transaction for rollback. Keeping
   * this attempt in its own transaction lets the fallback in {@link #updateExistingCompletion}
   * still run cleanly in the caller's transaction after the failure.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected StudentInteraction saveCompletion(User user, Content content) {
    StudentInteraction interaction = studentInteractionRepository.findByUserAndContent(user, content)
        .orElseGet(() -> new StudentInteraction(user, content));
    interaction.markCompleted();
    return studentInteractionRepository.save(interaction);
  }

  private StudentInteraction updateExistingCompletion(User user, Content content) {
    StudentInteraction interaction = studentInteractionRepository.findByUserAndContent(user, content)
        .orElseThrow(() -> new IllegalStateException(
            "Expected a concurrently-inserted StudentInteraction for user " + user.getEmail()
                + " and content " + content.getId() + " but found none"));
    interaction.markCompleted();
    return studentInteractionRepository.save(interaction);
  }

  private boolean isLocked(Module module, String requesterEmail) {
    return !hasCompletedDependencies(module, requesterEmail);
  }

  /**
   * True if the given student has completed every content item in every module that the given
   * module depends on. A module with no dependencies is always considered complete (i.e. not
   * locked). Teachers are not subject to this check at all; callers must decide separately
   * whether to apply it based on the requester's role.
   */
  @Transactional(readOnly = true)
  public boolean hasCompletedDependencies(Module module, String studentEmail) {
    User user = userService.getUserByEmail(studentEmail);
    LocalDateTime now = LocalDateTime.now(HandInPolicy.ZONE);
    for (Module dependency : module.getDependencies()) {
      List<Content> contents = contentRepository.findByModuleOrderByPosition(dependency);
      for (Content content : contents) {
        // Only what students can see right now can be required: a draft quiz, or content outside
        // its visibility window (not open yet, or already closed) would otherwise lock the next
        // module until it opens -- or forever, for a student who missed it.
        if (!content.isVisibleAt(now)) {
          continue;
        }
        boolean completed = studentInteractionRepository.findByUserAndContent(user, content)
            .map(interaction -> interaction.getCompletedAt() != null)
            .orElse(false);
        if (!completed) {
          return false;
        }
      }
    }
    return true;
  }
}
