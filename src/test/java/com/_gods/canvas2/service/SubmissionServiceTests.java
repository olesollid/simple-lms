package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Submission;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({SubmissionService.class, ContentService.class, ModuleService.class,
    EnrollmentService.class, UserService.class, StudentInteractionService.class,
    GroupService.class})
class SubmissionServiceTests {
  @Autowired
  private jakarta.persistence.EntityManager entityManager;

  private com._gods.canvas2.model.SubmissionFile pdf(String name) {
    return new com._gods.canvas2.model.SubmissionFile(name,
        "%PDF-1.4 test document".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void fileOnlyHandInPersistsBlobAndRestrictsDownloads() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    String other = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission saved = submissionService.submit(assignment.getId(), "", pdf("essay.pdf"), student);
    submissionService.submit(assignment.getId(), "Other student's work", other);
    Long id = saved.getId();
    entityManager.flush();
    entityManager.clear();
    var file = submissionService.downloadFile(id, teacher);
    assertEquals("essay.pdf", file.getFilename());
    org.junit.jupiter.api.Assertions.assertArrayEquals(pdf("essay.pdf").getData(), file.getData());
    assertEquals("essay.pdf", submissionService.downloadFile(id, student).getFilename());
    assertThrows(ForbiddenException.class, () -> submissionService.downloadFile(id, other));
    assertThrows(ForbiddenException.class,
        () -> submissionService.downloadFile(id, "stranger@example.com"));
  }

  @Test
  void codingExercisesAcceptFilesAndTextOnlyReplacementRemovesAttachment() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Module module = moduleRepository.save(new Module(course, "Code"));
    var coding = contentService.createCodingExercise(module.getId(), "Exercise", 0,
        "https://example.com/repo", null, false, false, teacher);
    Submission saved = submissionService.submit(coding.getId(), "Report", pdf("report.pdf"), student);
    submissionService.submit(coding.getId(), "Text replacement", student);
    assertThrows(NotFoundException.class, () -> submissionService.downloadFile(saved.getId(), teacher));
  }

  @Test
  void groupFileIsSharedReplacedAndLockedAfterGrading() {
    Course course = course();
    String teacher = teacherOf(course);
    String alice = studentOf(course);
    String bob = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);
    Group group = groupService.createGroup(course.getId(), "Team", teacher);
    groupService.addMember(group.getId(), alice, teacher);
    groupService.addMember(group.getId(), bob, teacher);
    Submission saved = submissionService.submit(assignment.getId(), "", pdf("draft.pdf"), alice);
    assertEquals("draft.pdf", submissionService.downloadFile(saved.getId(), bob).getFilename());
    Submission replaced = submissionService.submit(assignment.getId(), "", pdf("final.pdf"), bob);
    assertEquals(saved.getId(), replaced.getId());
    assertEquals("final.pdf", submissionService.downloadFile(saved.getId(), alice).getFilename());
    submissionService.grade(saved.getId(), 90, "Good", teacher);
    assertThrows(ConflictException.class,
        () -> submissionService.submit(assignment.getId(), "", pdf("new.pdf"), alice));
    assertEquals("final.pdf", submissionService.downloadFile(saved.getId(), teacher).getFilename());
  }

  @Autowired
  private SubmissionService submissionService;

  @Autowired
  private ContentService contentService;

  @Autowired
  private GroupService groupService;

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  private static int userCounter = 0;

  private Course course() {
    return courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
  }

  private String teacherOf(Course course) {
    String email = "teacher" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Teacher"));
    enrollmentRepository.save(new Enrollment(user, course, Role.TEACHER));
    return email;
  }

  private String studentOf(Course course) {
    String email = "student" + (userCounter++) + "@example.com";
    User user = userRepository.save(new User(email, "Student"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));
    return email;
  }

  private Assignment assignmentIn(Course course, String teacher) {
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return contentService.createAssignment(module.getId(), "Essay", 0, "Write 500 words", null,
        false, false, teacher);
  }

  private Assignment groupAssignmentIn(Course course, String teacher) {
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    return contentService.createAssignment(module.getId(), "Group Essay", 0, "Write 500 words",
        null, false, true, teacher);
  }

  @Test
  void publicationIsTeacherOnlyIdempotentAndScopedToContent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment first = assignmentIn(course, teacher);
    Assignment second = assignmentIn(course, teacher);
    assertFalse(submissionService.gradesPublished(first.getId(), teacher));
    assertThrows(ForbiddenException.class,
        () -> submissionService.publishGrades(first.getId(), student));
    String otherTeacher = teacherOf(course());
    assertThrows(ForbiddenException.class,
        () -> submissionService.publishGrades(first.getId(), otherTeacher));
    assertTrue(submissionService.publishGrades(first.getId(), teacher));
    assertTrue(submissionService.publishGrades(first.getId(), teacher));
    assertTrue(submissionService.gradesPublished(first.getId(), teacher));
    assertFalse(submissionService.gradesPublished(second.getId(), teacher));
  }

  @Test
  void groupMembersSeeResultsOnlyAfterPublication() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);
    Group group = groupService.createGroup(course.getId(), "Team", teacher);
    groupService.addMember(group.getId(), student, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "Team answer", student);
    submissionService.grade(submission.getId(), 90, "Team feedback", teacher);
    var privateResponse = com._gods.canvas2.web.dto.SubmissionResponse.forStudent(
        submissionService.getOwnSubmission(assignment.getId(), student));
    assertEquals(null, privateResponse.grade());
    assertEquals(null, privateResponse.feedback());
    submissionService.publishGrades(assignment.getId(), teacher);
    var publishedResponse = com._gods.canvas2.web.dto.SubmissionResponse.forStudent(
        submissionService.getOwnSubmission(assignment.getId(), student));
    assertEquals(90.0, publishedResponse.grade());
  }

  @Test
  void submitsAssignment() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);

    Submission submission = submissionService.submit(assignment.getId(), "My essay", student);

    assertEquals("My essay", submission.getText());
    assertFalse(submission.isGraded());
  }

  @Test
  void acceptsLongSubmissionTextAndFeedback() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    String essay = "x".repeat(10_000);
    String feedback = "y".repeat(5_000);

    Submission submission = submissionService.submit(assignment.getId(), essay, student);
    Submission graded = submissionService.grade(submission.getId(), 90, feedback, teacher);

    assertEquals(essay, submission.getText());
    assertEquals(feedback, graded.getFeedback());
  }

  @Test
  void submittingMarksContentCompleted() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);

    submissionService.submit(assignment.getId(), "My essay", student);

    List<Submission> submissions = submissionService.getSubmissionsByContent(assignment.getId(),
        teacher);
    assertEquals(1, submissions.size());
  }

  @Test
  void resubmittingUpdatesTheSameUngradedSubmission() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);

    Submission first = submissionService.submit(assignment.getId(), "Draft", student);
    Submission second = submissionService.submit(assignment.getId(), "Final", student);

    assertEquals(first.getId(), second.getId());
    assertEquals("Final", second.getText());
  }

  @Test
  void rejectsResubmittingAGradedSubmission() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "Draft", student);
    submissionService.grade(submission.getId(), 90, "Good work", teacher);

    assertThrows(ConflictException.class,
        () -> submissionService.submit(assignment.getId(), "Changed my mind", student));
  }

  @Test
  void rejectsSubmittingWhenNotEnrolled() {
    Course course = course();
    String teacher = teacherOf(course);
    Assignment assignment = assignmentIn(course, teacher);

    assertThrows(ForbiddenException.class,
        () -> submissionService.submit(assignment.getId(), "My essay", "stranger@example.com"));
  }

  @Test
  void throwsWhenSubmittingToMissingContent() {
    Course course = course();
    String student = studentOf(course);

    assertThrows(ContentNotFoundException.class,
        () -> submissionService.submit(999L, "My essay", student));
  }

  @Test
  void teacherGradesASubmission() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "My essay", student);

    Submission graded = submissionService.grade(submission.getId(), 85.5, "Well done", teacher);

    assertEquals(85.5, graded.getGrade());
    assertEquals("Well done", graded.getFeedback());
    assertTrue(graded.isGraded());
    assertNotNull(graded.getGradedAt());
  }

  @Test
  void rejectsGradesOutsideZeroToHundred() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "My essay", student);

    assertThrows(IllegalArgumentException.class,
        () -> submissionService.grade(submission.getId(), 100.5, "Too high", teacher));
    assertThrows(IllegalArgumentException.class,
        () -> submissionService.grade(submission.getId(), -1, "Too low", teacher));
    assertFalse(submission.isGraded());
  }

  @Test
  void acceptsBoundaryGradesAndRegrading() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "My essay", student);

    assertEquals(0.0, submissionService.grade(submission.getId(), 0, "First", teacher).getGrade());
    Submission regraded = submissionService.grade(submission.getId(), 100, "Regraded", teacher);

    assertEquals(100.0, regraded.getGrade());
    assertEquals("Regraded", regraded.getFeedback());
  }

  @Test
  void rejectsGradingByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "My essay", student);

    assertThrows(ForbiddenException.class,
        () -> submissionService.grade(submission.getId(), 100, "Nice try", student));
  }

  @Test
  void studentCanSeeTheirOwnSubmission() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    submissionService.submit(assignment.getId(), "My essay", student);

    Submission own = submissionService.getOwnSubmission(assignment.getId(), student);

    assertEquals("My essay", own.getText());
  }

  @Test
  void rejectsListingSubmissionsByStudent() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = assignmentIn(course, teacher);
    submissionService.submit(assignment.getId(), "My essay", student);

    assertThrows(ForbiddenException.class,
        () -> submissionService.getSubmissionsByContent(assignment.getId(), student));
  }

  @Test
  void groupSubmissionIsSharedAcrossMembers() {
    Course course = course();
    String teacher = teacherOf(course);
    String alice = studentOf(course);
    String bob = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), alice, teacher);
    groupService.addMember(group.getId(), bob, teacher);

    submissionService.submit(assignment.getId(), "Alice's draft", alice);
    Submission afterBob = submissionService.submit(assignment.getId(), "Bob's final version", bob);

    assertEquals("Bob's final version", afterBob.getText());
    assertEquals(bob, afterBob.getLastSubmittedBy().getEmail());
    List<Submission> all = submissionService.getSubmissionsByContent(assignment.getId(), teacher);
    assertEquals(1, all.size());
  }

  @Test
  void bothGroupMembersSeeTheSameOwnSubmission() {
    Course course = course();
    String teacher = teacherOf(course);
    String alice = studentOf(course);
    String bob = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), alice, teacher);
    groupService.addMember(group.getId(), bob, teacher);
    submissionService.submit(assignment.getId(), "Shared draft", alice);

    Submission fromAlice = submissionService.getOwnSubmission(assignment.getId(), alice);
    Submission fromBob = submissionService.getOwnSubmission(assignment.getId(), bob);

    assertEquals(fromAlice.getId(), fromBob.getId());
    assertEquals("Shared draft", fromBob.getText());
  }

  @Test
  void rejectsGroupSubmissionWhenStudentHasNoGroup() {
    Course course = course();
    String teacher = teacherOf(course);
    String student = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);

    assertThrows(NotFoundException.class,
        () -> submissionService.submit(assignment.getId(), "My essay", student));
  }

  @Test
  void gradingAGroupSubmissionGradesItForEveryMember() {
    Course course = course();
    String teacher = teacherOf(course);
    String alice = studentOf(course);
    String bob = studentOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);
    Group group = groupService.createGroup(course.getId(), "Team A", teacher);
    groupService.addMember(group.getId(), alice, teacher);
    groupService.addMember(group.getId(), bob, teacher);
    Submission submission = submissionService.submit(assignment.getId(), "Shared draft", alice);

    submissionService.grade(submission.getId(), 88, "Nice teamwork", teacher);

    assertTrue(submissionService.getOwnSubmission(assignment.getId(), bob).isGraded());
  }

  @Test
  void teacherSubmittingToGroupContentIsRecordedIndividually() {
    Course course = course();
    String teacher = teacherOf(course);
    Assignment assignment = groupAssignmentIn(course, teacher);

    Submission submission = submissionService.submit(assignment.getId(), "Model answer", teacher);

    assertEquals(teacher, submission.getSubmitter().getEmail());
    assertEquals(teacher, submissionService.getOwnSubmission(assignment.getId(), teacher)
        .getSubmitter().getEmail());
  }
}
