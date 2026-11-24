package com._gods.simplelms.service;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.simplelms.grading.QuizGradeStore;
import com._gods.simplelms.model.Assignment;
import com._gods.simplelms.model.CodingExercise;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.repository.ContentRepository;
import com._gods.simplelms.repository.GroupMembershipRepository;
import com._gods.simplelms.repository.SubmissionRepository;
import com._gods.simplelms.web.dto.ContentResponse;
import com._gods.simplelms.web.dto.StudentDeliveriesResponse;
import com._gods.simplelms.web.dto.SubmissionResponse;
import com._gods.simplelms.web.dto.StudentDeliveriesResponse.Delivery;
import com._gods.simplelms.web.dto.StudentDeliveriesResponse.QuizSummary;

@Service
@Transactional(readOnly = true)
public class StudentDeliveriesService {
  private final EnrollmentService enrollments;
  private final CourseService courses;
  private final ModuleService modules;
  private final ContentRepository contents;
  private final SubmissionRepository submissions;
  private final GroupMembershipRepository memberships;
  private final QuizGradeStore quizGrades;

  public StudentDeliveriesService(EnrollmentService enrollments, CourseService courses,
      ModuleService modules, ContentRepository contents, SubmissionRepository submissions,
      GroupMembershipRepository memberships, QuizGradeStore quizGrades) {
    this.enrollments = enrollments;
    this.courses = courses;
    this.modules = modules;
    this.contents = contents;
    this.submissions = submissions;
    this.memberships = memberships;
    this.quizGrades = quizGrades;
  }

  public StudentDeliveriesResponse getDeliveries(Long courseId, String studentEmail, String requesterEmail) {
    if (!enrollments.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException("Only a teacher in this course may review student deliveries");
    }
    var enrollment = enrollments.getEnrollment(courseId, studentEmail, requesterEmail);
    if (enrollment.getRole() != Role.STUDENT) {
      throw new NotFoundException("This user is not enrolled as a student in this course");
    }
    var student = enrollment.getUser();
    var course = courses.getCourseById(courseId, requesterEmail);
    var group = memberships.findByUserAndGroupCourse(student, course).stream()
        .findFirst().map(membership -> membership.getGroup()).orElse(null);
    var deliveries = new ArrayList<Delivery>();
    LocalDateTime now = LocalDateTime.now(HandInPolicy.ZONE);
    for (var module : modules.getModulesByCourse(courseId, requesterEmail)) {
      boolean unlocked = !modules.isLocked(module, student.getEmail());
      for (var content : contents.findByModuleOrderByPosition(module)) {
        boolean available = unlocked && content.isVisibleAt(now);
        if (content instanceof Quiz quiz) {
          int used = quizGrades.attemptsUsed(quiz, student.getEmail());
          var best = quizGrades.find(quiz, student.getEmail()).orElse(null);
          var latest = quizGrades.findLatest(quiz, student.getEmail()).orElse(null);
          deliveries.add(new Delivery(ContentResponse.from(content), module.getTitle(), available,
              quiz.areResultsVisibleToStudents(), null,
              new QuizSummary(used, latest == null ? 0 : latest.attemptNumber(), best == null ? null : best.score())));
        } else if (content instanceof Assignment || content instanceof CodingExercise) {
          boolean shared = content instanceof Assignment assignment ? assignment.isGroupSubmission()
              : ((CodingExercise) content).isGroupSubmission();
          // Empty lookups are ordinary missing work, not transactional service failures.
          // Ownership follows the same current-group semantics as the student's own view.
          var submitted = shared
              ? group == null ? null : submissions.findByContentAndGroup(content, group).orElse(null)
              : submissions.findByContentAndSubmitter(content, student).orElse(null);
          deliveries.add(new Delivery(ContentResponse.from(content), module.getTitle(), available,
              content.isGradesPublished(), submitted == null ? null : SubmissionResponse.from(submitted), null));
        }
      }
    }
    return new StudentDeliveriesResponse(courseId, course.getTitle(), student.getEmail(), student.getName(), deliveries);
  }
}
