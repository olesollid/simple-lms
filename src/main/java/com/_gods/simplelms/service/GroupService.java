package com._gods.simplelms.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.GroupMembership;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.CourseRepository;
import com._gods.simplelms.repository.GroupMembershipRepository;
import com._gods.simplelms.repository.GroupRepository;
import com._gods.simplelms.repository.SubmissionRepository;

/**
 * Groups a course's students into project teams for group-submittable
 * {@link com._gods.simplelms.model.Assignment}/{@link com._gods.simplelms.model.CodingExercise}
 * content. A student belongs to at most one group per course, matching a typical
 * project-team model; membership changes are teacher-only.
 */
@Service
@Transactional
public class GroupService {

  private final GroupRepository groupRepository;
  private final GroupMembershipRepository groupMembershipRepository;
  private final CourseRepository courseRepository;
  private final SubmissionRepository submissionRepository;
  private final UserService userService;
  private final EnrollmentService enrollmentService;

  public GroupService(GroupRepository groupRepository,
      GroupMembershipRepository groupMembershipRepository, CourseRepository courseRepository,
      SubmissionRepository submissionRepository, UserService userService, EnrollmentService enrollmentService) {
    this.groupRepository = groupRepository;
    this.groupMembershipRepository = groupMembershipRepository;
    this.courseRepository = courseRepository;
    this.submissionRepository = submissionRepository;
    this.userService = userService;
    this.enrollmentService = enrollmentService;
  }

  public Group createGroup(Long courseId, String name, String requesterEmail) {
    Course course = getCourse(courseId);
    requireTeacher(courseId, requesterEmail);
    return groupRepository.save(new Group(course, name));
  }

  /**
   * Deletes a group and its memberships. A group that already has shared submissions cannot be
   * deleted, since that would orphan (or silently destroy) graded work.
   */
  public void deleteGroup(Long groupId, String requesterEmail) {
    Group group = getGroup(groupId);
    requireTeacher(group.getCourse().getId(), requesterEmail);
    if (submissionRepository.existsByGroup(group)) {
      throw new ConflictException(
          "Group " + groupId + " has submissions and cannot be deleted");
    }
    groupMembershipRepository.deleteAll(groupMembershipRepository.findByGroup(group));
    groupRepository.delete(group);
  }

  public GroupMembership addMember(Long groupId, String email, String requesterEmail) {
    Group group = getGroup(groupId);
    Long courseId = group.getCourse().getId();
    requireTeacher(courseId, requesterEmail);
    if (!enrollmentService.hasRole(courseId, email, Role.STUDENT)) {
      throw new ConflictException(
          email + " must be enrolled as a student in course " + courseId + " to join a group");
    }
    User user = userService.getUserByEmail(email);
    if (!groupMembershipRepository.findByUserAndGroupCourse(user, group.getCourse()).isEmpty()) {
      throw new ConflictException(
          email + " already belongs to a group in course " + courseId);
    }
    return groupMembershipRepository.save(new GroupMembership(user, group));
  }

  public void removeMember(Long groupId, String email, String requesterEmail) {
    Group group = getGroup(groupId);
    requireTeacher(group.getCourse().getId(), requesterEmail);
    User user = userService.getUserByEmail(email);
    GroupMembership membership = groupMembershipRepository.findByGroup(group).stream()
        .filter(candidate -> candidate.getUser().getEmail().equals(user.getEmail()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(
            email + " is not a member of group " + groupId));
    groupMembershipRepository.delete(membership);
  }

  @Transactional(readOnly = true)
  public List<Group> getGroupsByCourse(Long courseId, String requesterEmail) {
    Course course = getCourse(courseId);
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    return groupRepository.findByCourse(course);
  }

  @Transactional(readOnly = true)
  public List<GroupMembership> getMembers(Long groupId, String requesterEmail) {
    Group group = getGroup(groupId);
    enrollmentService.requireEnrolled(group.getCourse().getId(), requesterEmail);
    return groupMembershipRepository.findByGroup(group);
  }

  /** The requester's own group within the course, if any. */
  @Transactional(readOnly = true)
  public Group getOwnGroup(Long courseId, String requesterEmail) {
    Course course = getCourse(courseId);
    enrollmentService.requireEnrolled(courseId, requesterEmail);
    User user = userService.getUserByEmail(requesterEmail);
    return groupMembershipRepository.findByUserAndGroupCourse(user, course).stream()
        .findFirst()
        .map(GroupMembership::getGroup)
        .orElseThrow(() -> new NotFoundException(
            requesterEmail + " does not belong to a group in course " + courseId));
  }

  private Group getGroup(Long id) {
    return groupRepository.findById(id)
        .orElseThrow(() -> new GroupNotFoundException(id));
  }

  private Course getCourse(Long courseId) {
    return courseRepository.findById(courseId)
        .orElseThrow(() -> new CourseNotFoundException(courseId));
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
