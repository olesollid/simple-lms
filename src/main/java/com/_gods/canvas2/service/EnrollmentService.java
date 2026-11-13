package com._gods.canvas2.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.GroupMembershipRepository;

/**
 * Roles are course-specific: a user has at most one enrollment per course, and
 * that enrollment's role applies only within that course.
 */
@Service
@Transactional
public class EnrollmentService {

  private final EnrollmentRepository enrollmentRepository;
  private final CourseRepository courseRepository;
  private final GroupMembershipRepository groupMembershipRepository;
  private final UserService userService;

  public EnrollmentService(EnrollmentRepository enrollmentRepository,
      CourseRepository courseRepository, GroupMembershipRepository groupMembershipRepository,
      UserService userService) {
    this.enrollmentRepository = enrollmentRepository;
    this.courseRepository = courseRepository;
    this.groupMembershipRepository = groupMembershipRepository;
    this.userService = userService;
  }

  public Enrollment enroll(Long courseId, String email, Role role, String requesterEmail) {
    requireTeacher(courseId, requesterEmail);
    Course course = getCourse(courseId);
    User user = userService.getUserByEmail(email);
    if (enrollmentRepository.findByUserAndCourse(user, course).isPresent()) {
      throw new ConflictException(
          "User " + user.getEmail() + " is already enrolled in course " + courseId);
    }
    return enrollmentRepository.save(new Enrollment(user, course, role));
  }

  /** Used for course-creator self-enrollment, which has no requester to check against. */
  Enrollment enrollWithoutAuthorization(Long courseId, String email, Role role) {
    Course course = getCourse(courseId);
    User user = userService.getUserByEmail(email);
    return enrollmentRepository.save(new Enrollment(user, course, role));
  }

  /** Any enrolled member (either role) may view the full roster. */
  @Transactional(readOnly = true)
  public List<Enrollment> getEnrollmentsByCourse(Long courseId, String requesterEmail) {
    requireEnrolled(courseId, requesterEmail);
    return enrollmentRepository.findByCourse(getCourse(courseId));
  }

  @Transactional(readOnly = true)
  public List<Enrollment> getEnrollmentsByCourse(Long courseId, Role role, String requesterEmail) {
    requireEnrolled(courseId, requesterEmail);
    return enrollmentRepository.findByCourseAndRole(getCourse(courseId), role);
  }

  /** Self-only: a user may list only their own enrollments. */
  @Transactional(readOnly = true)
  public List<Enrollment> getEnrollmentsByUser(String email, String requesterEmail) {
    requireSelf(email, requesterEmail);
    return enrollmentRepository.findByUser(userService.getUserByEmail(email));
  }

  @Transactional(readOnly = true)
  public Enrollment getEnrollment(Long courseId, String email, String requesterEmail) {
    requireEnrolled(courseId, requesterEmail);
    return getEnrollment(courseId, email);
  }

  private Enrollment getEnrollment(Long courseId, String email) {
    Course course = getCourse(courseId);
    User user = userService.getUserByEmail(email);
    return enrollmentRepository.findByUserAndCourse(user, course)
        .orElseThrow(() -> new NotFoundException(
            "User " + user.getEmail() + " is not enrolled in course " + courseId));
  }

  @Transactional(readOnly = true)
  public boolean hasRole(Long courseId, String email, Role role) {
    Course course = getCourse(courseId);
    User user;
    try {
      user = userService.getUserByEmail(email);
    } catch (NotFoundException e) {
      return false;
    }
    return enrollmentRepository.findByUserAndCourse(user, course)
        .map(enrollment -> enrollment.getRole() == role)
        .orElse(false);
  }

  /** True if the user has any enrollment (either role) in the course. */
  @Transactional(readOnly = true)
  public boolean isEnrolled(Long courseId, String email) {
    Course course = getCourse(courseId);
    User user;
    try {
      user = userService.getUserByEmail(email);
    } catch (NotFoundException e) {
      return false;
    }
    return enrollmentRepository.findByUserAndCourse(user, course).isPresent();
  }

  /** Shared read-access guard: any role enrolled in the course may read its contents. */
  public void requireEnrolled(Long courseId, String requesterEmail) {
    if (!isEnrolled(courseId, requesterEmail)) {
      throw new ForbiddenException(
          "Only a member of course " + courseId + " may view this resource");
    }
  }

  public Enrollment changeRole(Long courseId, String email, Role role, String requesterEmail) {
    boolean self = User.normalizeEmail(email).equals(User.normalizeEmail(requesterEmail));
    if (!self) {
      requireTeacher(courseId, requesterEmail);
    }
    Enrollment enrollment = getEnrollment(courseId, email);
    if (enrollment.getRole() == Role.TEACHER && role != Role.TEACHER) {
      requireNotLastTeacher(courseId, enrollment);
    }
    enrollment.setRole(role);
    return enrollmentRepository.save(enrollment);
  }

  /** Leaving a course also removes the user from that course's groups. */
  public void unenroll(Long courseId, String email, String requesterEmail) {
    boolean self = User.normalizeEmail(email).equals(User.normalizeEmail(requesterEmail));
    if (!self) {
      requireTeacher(courseId, requesterEmail);
    }
    Enrollment enrollment = getEnrollment(courseId, email);
    if (enrollment.getRole() == Role.TEACHER) {
      requireNotLastTeacher(courseId, enrollment);
    }
    groupMembershipRepository.deleteAll(
        groupMembershipRepository.findByUserAndGroupCourse(enrollment.getUser(),
            enrollment.getCourse()));
    enrollmentRepository.delete(enrollment);
  }

  private void requireSelf(String email, String requesterEmail) {
    if (!User.normalizeEmail(email).equals(User.normalizeEmail(requesterEmail))) {
      throw new ForbiddenException("Users may only view their own enrollments");
    }
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }

  /** A course must always keep at least one teacher able to manage it. */
  private void requireNotLastTeacher(Long courseId, Enrollment departing) {
    long teacherCount = enrollmentRepository.findByCourseAndRole(departing.getCourse(), Role.TEACHER)
        .size();
    if (teacherCount <= 1) {
      throw new ConflictException(
          "Course " + courseId + " must keep at least one teacher");
    }
  }

  private Course getCourse(Long courseId) {
    return courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
  }
}
