package com._gods.canvas2.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.grading.QuizGradeStore;
import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.HandInPolicy;
import com._gods.canvas2.model.Quiz;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.repository.ContentRepository;
import com._gods.canvas2.repository.GroupMembershipRepository;
import com._gods.canvas2.repository.SubmissionRepository;
import com._gods.canvas2.web.dto.MyDeliveriesResponse;
import com._gods.canvas2.web.dto.MyDeliveriesResponse.Activity;
import com._gods.canvas2.web.dto.MyDeliveriesResponse.Status;

@Service
@Transactional(readOnly = true)
public class MyDeliveriesService {
  private final EnrollmentService enrollments;
  private final ModuleService modules;
  private final ContentRepository contents;
  private final SubmissionRepository submissions;
  private final GroupMembershipRepository memberships;
  private final QuizGradeStore quizGrades;

  public MyDeliveriesService(EnrollmentService enrollments, ModuleService modules,
      ContentRepository contents, SubmissionRepository submissions,
      GroupMembershipRepository memberships, QuizGradeStore quizGrades) {
    this.enrollments = enrollments;
    this.modules = modules;
    this.contents = contents;
    this.submissions = submissions;
    this.memberships = memberships;
    this.quizGrades = quizGrades;
  }

  public MyDeliveriesResponse getOwnDeliveries(Long courseId, String requesterEmail) {
    var enrollment = enrollments.getEnrollment(courseId, requesterEmail, requesterEmail);
    if (enrollment.getRole() == Role.TEACHER) {
      return new MyDeliveriesResponse(courseId, Role.TEACHER, List.of());
    }
    var student = enrollment.getUser();
    var group = memberships.findByUserAndGroupCourse(student, enrollment.getCourse()).stream()
        .findFirst().map(member -> member.getGroup()).orElse(null);
    var activities = new ArrayList<Activity>();
    LocalDateTime now = LocalDateTime.now(HandInPolicy.ZONE);
    for (var module : modules.getModulesByCourse(courseId, requesterEmail)) {
      if (modules.isLocked(module, requesterEmail)) continue;
      for (var content : contents.findByModuleOrderByPosition(module)) {
        if (!content.isVisibleAt(now)) continue;
        if (content instanceof Quiz quiz) {
          int attempts = quizGrades.attemptsUsed(quiz, student.getEmail());
          var latest = quizGrades.findLatest(quiz, student.getEmail()).orElse(null);
          // Never retrieve a grade for the public response until publication allows it.
          var best = quiz.areResultsVisibleToStudents()
              ? quizGrades.find(quiz, student.getEmail()).orElse(null) : null;
          boolean pending = attempts > (latest == null ? 0 : latest.attemptNumber());
          Status status = attempts == 0 ? Status.NOT_SUBMITTED : best != null ? Status.GRADED
              : latest != null && !quiz.areResultsVisibleToStudents()
              ? Status.AWAITING_PUBLICATION : Status.AWAITING_GRADING;
          activities.add(new Activity(content.getId(), module.getId(), content.getTitle(), "Quiz",
              quiz.getDeadline(), status, best == null ? null : best.score() * 100, null, null,
              false, false, pending, status == Status.NOT_SUBMITTED && quiz.isClosedAt(now)));
        } else if (content instanceof Assignment || content instanceof CodingExercise) {
          boolean shared = content instanceof Assignment assignment ? assignment.isGroupSubmission()
              : ((CodingExercise) content).isGroupSubmission();
          var submission = shared
              ? group == null ? null : submissions.findByContentAndGroup(content, group).orElse(null)
              : submissions.findByContentAndSubmitter(content, student).orElse(null);
          boolean published = content.isGradesPublished();
          Status status = submission == null ? Status.NOT_SUBMITTED : !submission.isGraded()
              ? Status.AWAITING_GRADING : published ? Status.GRADED : Status.AWAITING_PUBLICATION;
          LocalDateTime deadline = HandInPolicy.deadline(content);
          activities.add(new Activity(content.getId(), module.getId(), content.getTitle(),
              content instanceof Assignment ? "Assignment" : "CodingExercise", deadline, status,
              submission != null && published ? submission.getGrade() : null,
              submission != null && published ? submission.getFeedback() : null,
              submission == null ? null : submission.getLateSubmission(), shared, shared && group == null,
              false, submission == null && deadline != null && now.isAfter(deadline)));
        }
      }
    }
    return new MyDeliveriesResponse(courseId, Role.STUDENT, activities);
  }
}
