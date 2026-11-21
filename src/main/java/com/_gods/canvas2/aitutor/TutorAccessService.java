package com._gods.canvas2.aitutor;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.service.ContentNotFoundException;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.service.UserService;

/**
 * Decides who may use the AI tutor and verifies the page context a request claims. The frontend's
 * ids are only hints: every request is checked here against enrollments and content visibility,
 * and a failed check never reveals whether the referenced course or assignment exists.
 */
@Service
@Transactional(readOnly = true)
public class TutorAccessService {

  private final AiTutorProperties properties;
  private final EnrollmentRepository enrollmentRepository;
  private final CourseRepository courseRepository;
  private final UserService userService;
  private final ContentService contentService;

  public TutorAccessService(AiTutorProperties properties, EnrollmentRepository enrollmentRepository,
      CourseRepository courseRepository, UserService userService, ContentService contentService) {
    this.properties = properties;
    this.enrollmentRepository = enrollmentRepository;
    this.courseRepository = courseRepository;
    this.userService = userService;
    this.contentService = contentService;
  }

  /**
   * Whether the tutor is on for this user. With a course: the global switch, that course's opt-in,
   * and a student enrollment in it. Without one (dashboard): the global switch and a student
   * enrollment in at least one opted-in course. Never throws; anything unexpected means "off".
   */
  public boolean isEnabledFor(String email, Long courseId) {
    if (!properties.enabled()) {
      return false;
    }
    Optional<User> user = findUser(email);
    if (user.isEmpty()) {
      return false;
    }
    if (courseId == null) {
      return enrollmentRepository.findByUser(user.get()).stream()
          .anyMatch(enrollment -> enrollment.getRole() == Role.STUDENT
              && properties.isCourseEnabled(enrollment.getCourse().getId()));
    }
    if (!properties.isCourseEnabled(courseId)) {
      return false;
    }
    return courseRepository.findById(courseId)
        .flatMap(course -> enrollmentRepository.findByUserAndCourse(user.get(), course))
        .map(enrollment -> enrollment.getRole() == Role.STUDENT)
        .orElse(false);
  }

  /**
   * Checks the claimed context for this student and returns what the server found. Dashboard and
   * "other" pages carry no ids, and any sent are ignored rather than looked up.
   *
   * @throws ForbiddenException the tutor is off, or the student is not an enrolled student of the course
   * @throws ContentNotFoundException the assignment does not exist, is hidden from the student, is not
   *     an assignment, or belongs to another course
   * @throws IllegalArgumentException the context is missing a field its page type needs
   */
  public VerifiedTutorContext verify(String email, TutorContextRequest request) {
    if (request == null || request.pageType() == null) {
      throw new IllegalArgumentException("page_type is required");
    }
    TutorPageType pageType = request.pageType();
    if (pageType == TutorPageType.DASHBOARD || pageType == TutorPageType.OTHER) {
      if (!isEnabledFor(email, null)) {
        throw new ForbiddenException("The AI tutor is not available to you.");
      }
      return new VerifiedTutorContext(pageType, null, null, null, null);
    }

    if (request.courseId() == null) {
      throw new IllegalArgumentException("course_id is required");
    }
    if (pageType == TutorPageType.ASSIGNMENT && request.assignmentId() == null) {
      throw new IllegalArgumentException("assignment_id is required");
    }
    Course course = requireStudentOfEnabledCourse(email, request.courseId());
    if (pageType == TutorPageType.COURSE) {
      return new VerifiedTutorContext(pageType, course.getId(), course.getTitle(), null, null);
    }

    Content assignment = findVisibleAssignment(email, course, request.assignmentId());
    return new VerifiedTutorContext(pageType, course.getId(), course.getTitle(),
        assignment.getId(), assignment.getTitle());
  }

  private Course requireStudentOfEnabledCourse(String email, Long courseId) {
    // One message for every failure, so a caller cannot tell a missing course from a forbidden one.
    ForbiddenException refusal =
        new ForbiddenException("The AI tutor is not available for this course.");
    if (!properties.isCourseEnabled(courseId)) {
      throw refusal;
    }
    User user = findUser(email).orElseThrow(() -> refusal);
    Course course = courseRepository.findById(courseId).orElseThrow(() -> refusal);
    Enrollment enrollment = enrollmentRepository.findByUserAndCourse(user, course)
        .orElseThrow(() -> refusal);
    if (enrollment.getRole() != Role.STUDENT) {
      throw refusal;
    }
    return course;
  }

  private Content findVisibleAssignment(String email, Course course, Long assignmentId) {
    Content content;
    try {
      // Applies the normal rules: enrolled, module not locked for this student, inside its
      // visibility window.
      content = contentService.getContentById(assignmentId, email);
    } catch (ForbiddenException e) {
      // Belongs to a course this student is not in; do not confirm that it exists.
      throw new ContentNotFoundException(assignmentId);
    }
    boolean isAssignment = content instanceof Assignment || content instanceof CodingExercise;
    if (!isAssignment || !content.getModule().getCourse().getId().equals(course.getId())) {
      throw new ContentNotFoundException(assignmentId);
    }
    return content;
  }

  private Optional<User> findUser(String email) {
    try {
      return Optional.of(userService.getUserByEmail(email));
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }
}
