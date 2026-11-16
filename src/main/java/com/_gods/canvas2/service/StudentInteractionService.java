package com._gods.canvas2.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.StudentInteraction;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.StudentInteractionRepository;

/**
 * Tracks which content items a student has "interacted with" ({@link StudentInteraction}), used
 * to gate module visibility: a module is only visible once every content item in each module it
 * depends on has been marked completed by that student. For most content types completion is an
 * explicit action via {@link #markCompleted}; quiz completion is instead marked automatically
 * once grading finishes (see {@code SubmissionGradingListener}), since the submission itself is
 * the unambiguous "done" signal.
 */
@Service
@Transactional
public class StudentInteractionService {

  private final StudentInteractionRepository studentInteractionRepository;
  private final ContentRepository contentRepository;
  private final UserService userService;
  private final EnrollmentService enrollmentService;

  public StudentInteractionService(StudentInteractionRepository studentInteractionRepository,
      ContentRepository contentRepository, UserService userService,
      EnrollmentService enrollmentService) {
    this.studentInteractionRepository = studentInteractionRepository;
    this.contentRepository = contentRepository;
    this.userService = userService;
    this.enrollmentService = enrollmentService;
  }

  /** Any enrolled member may mark content as completed for themselves. */
  public StudentInteraction markCompleted(Long contentId, String requesterEmail) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(contentId));
    enrollmentService.requireEnrolled(content.getModule().getCourse().getId(), requesterEmail);
    User user = userService.getUserByEmail(requesterEmail);

    StudentInteraction interaction = studentInteractionRepository.findByUserAndContent(user, content)
        .orElseGet(() -> new StudentInteraction(user, content));
    interaction.markCompleted();
    return studentInteractionRepository.save(interaction);
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
    for (Module dependency : module.getDependencies()) {
      List<Content> contents = contentRepository.findByModuleOrderByPosition(dependency);
      for (Content content : contents) {
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
