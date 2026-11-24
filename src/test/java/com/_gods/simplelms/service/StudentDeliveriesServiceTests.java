package com._gods.simplelms.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com._gods.simplelms.grading.QuizGrade;
import com._gods.simplelms.grading.QuizGradeStore;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.HandInPolicy;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Quiz;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.SubmissionFile;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.service.ContentService;
import com._gods.simplelms.service.CourseService;
import com._gods.simplelms.service.EnrollmentService;
import com._gods.simplelms.service.ForbiddenException;
import com._gods.simplelms.service.GroupService;
import com._gods.simplelms.service.ModuleService;
import com._gods.simplelms.service.MyDeliveriesService;
import com._gods.simplelms.service.NotFoundException;
import com._gods.simplelms.service.StudentDeliveriesService;
import com._gods.simplelms.service.StudentInteractionService;
import com._gods.simplelms.service.SubmissionService;
import com._gods.simplelms.service.UserService;

@DataJpaTest
@Import({StudentDeliveriesService.class, MyDeliveriesService.class, EnrollmentService.class, UserService.class,
    CourseService.class, ModuleService.class, ContentService.class, SubmissionService.class,
    GroupService.class, StudentInteractionService.class})
class StudentDeliveriesServiceTests {
  @Autowired StudentDeliveriesService overview;
  @Autowired MyDeliveriesService myDeliveries;
  @Autowired UserService users;
  @Autowired CourseService courses;
  @Autowired EnrollmentService enrollments;
  @Autowired ModuleService modules;
  @Autowired ContentService contents;
  @Autowired SubmissionService submissions;
  @Autowired GroupService groups;
  @MockitoBean QuizGradeStore quizGrades;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  Course course;
  Module module;
  static final String TEACHER = "teacher@example.com";
  static final String ALICE = "alice@example.com";
  static final String BOB = "bob@example.com";

  @BeforeEach
  void setup() {
    users.createUser(TEACHER, "Teacher");
    users.createUser(ALICE, "Alice");
    users.createUser(BOB, "Bob");
    course = courses.createCourse("DAT250", "Course", Term.AUTUMN, 2026, TEACHER);
    enrollments.enroll(course.getId(), ALICE, Role.STUDENT, TEACHER);
    enrollments.enroll(course.getId(), BOB, Role.STUDENT, TEACHER);
    module = modules.createModule(course.getId(), "Week 1", TEACHER);
    when(quizGrades.find(any(Quiz.class), anyString())).thenReturn(Optional.empty());
    when(quizGrades.findLatest(any(Quiz.class), anyString())).thenReturn(Optional.empty());
  }

  @Test
  void ownSummaryRedactsUnpublishedGradesAndNeverReturnsAnotherStudentsHandIn() {
    var essay = contents.createAssignment(module.getId(), "Essay", 0, "Write", null, false, false, TEACHER);
    var own = submissions.submit(essay.getId(), "Alice work", ALICE);
    submissions.grade(own.getId(), 87, "Private feedback", TEACHER);
    var privateRow = myDeliveries.getOwnDeliveries(course.getId(), ALICE).activities().get(0);
    assertEquals(com._gods.simplelms.web.dto.MyDeliveriesResponse.Status.AWAITING_PUBLICATION, privateRow.status());
    assertNull(privateRow.grade());
    assertNull(privateRow.feedback());
    assertEquals(com._gods.simplelms.web.dto.MyDeliveriesResponse.Status.NOT_SUBMITTED,
        myDeliveries.getOwnDeliveries(course.getId(), BOB).activities().get(0).status());
    submissions.publishGrades(essay.getId(), TEACHER);
    var publishedRow = myDeliveries.getOwnDeliveries(course.getId(), ALICE).activities().get(0);
    assertEquals(87.0, publishedRow.grade());
    assertEquals("Private feedback", publishedRow.feedback());
  }

  @Test
  void ownSummaryCountsGroupHandInsAndMissingGroupCorrectly() {
    var report = contents.createAssignment(module.getId(), "Group work", 0, "Write",
        LocalDateTime.now(HandInPolicy.ZONE).minusHours(1), true, true, TEACHER);
    var missing = myDeliveries.getOwnDeliveries(course.getId(), BOB).activities().get(0);
    assertTrue(missing.needsGroup());
    assertTrue(missing.overdue());
    var group = groups.createGroup(course.getId(), "Team", TEACHER);
    groups.addMember(group.getId(), ALICE, TEACHER);
    groups.addMember(group.getId(), BOB, TEACHER);
    submissions.submit(report.getId(), "Team work", ALICE);
    var shared = myDeliveries.getOwnDeliveries(course.getId(), BOB).activities().get(0);
    assertEquals(com._gods.simplelms.web.dto.MyDeliveriesResponse.Status.AWAITING_GRADING, shared.status());
    assertTrue(shared.groupSubmission());
    assertTrue(shared.lateSubmission());
    assertFalse(shared.needsGroup());
    assertFalse(shared.overdue());
  }

  @Test
  void ownSummaryHidesLockedHiddenAndDraftActivitiesAndSeparatesTeacherCourses() {
    contents.createStaticPage(module.getId(), "Read", 0, "Read", null, null, TEACHER);
    var hidden = contents.createAssignment(module.getId(), "Hidden", 1, "Write", null, false, false, TEACHER);
    contents.updateContentMetadata(hidden.getId(), "Hidden", 1,
        LocalDateTime.now(HandInPolicy.ZONE).plusDays(1), null, TEACHER);
    contents.createQuiz(module.getId(), "Draft quiz", 2, 3, null, false, TEACHER);
    var locked = modules.createModule(course.getId(), "Locked", TEACHER);
    modules.addDependency(locked.getId(), module.getId(), TEACHER);
    contents.createAssignment(locked.getId(), "Secret", 0, "Write", null, false, false, TEACHER);
    assertTrue(myDeliveries.getOwnDeliveries(course.getId(), ALICE).activities().isEmpty());
    var teaching = myDeliveries.getOwnDeliveries(course.getId(), TEACHER);
    assertEquals(Role.TEACHER, teaching.role());
    assertTrue(teaching.activities().isEmpty());
    assertThrows(ForbiddenException.class, () -> myDeliveries.getOwnDeliveries(course.getId(), "stranger@example.com"));
    verifyNoInteractions(quizGrades);
  }

  @Test
  void ownQuizSummaryDoesNotLeakScoresBeforeResultsArePublished() {
    var quiz = contents.createQuiz(module.getId(), "Quiz", 0, 3, null, false, TEACHER);
    quiz.addQuestion("Question");
    quiz.publish();
    var grade = new QuizGrade(quiz.getId(), ALICE, 1, 3, 4, .75, List.of(), LocalDateTime.now());
    when(quizGrades.attemptsUsed(quiz, ALICE)).thenReturn(1);
    when(quizGrades.findLatest(quiz, ALICE)).thenReturn(Optional.of(grade));
    var hidden = myDeliveries.getOwnDeliveries(course.getId(), ALICE).activities().get(0);
    assertEquals(com._gods.simplelms.web.dto.MyDeliveriesResponse.Status.AWAITING_PUBLICATION, hidden.status());
    assertNull(hidden.grade());
    verify(quizGrades, never()).find(quiz, ALICE);
    quiz.publishGrades();
    when(quizGrades.find(quiz, ALICE)).thenReturn(Optional.of(grade));
    assertEquals(75.0, myDeliveries.getOwnDeliveries(course.getId(), ALICE).activities().get(0).grade());
  }

  @Test
  void missingSubmissionAndMissingGroupDoNotMarkTheTransactionForRollback() {
    contents.createAssignment(module.getId(), "Individual", 0, "Write", null, false, false, TEACHER);
    contents.createAssignment(module.getId(), "Group", 1, "Write", null, false, true, TEACHER);
    new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
      var rows = overview.getDeliveries(course.getId(), ALICE, TEACHER).deliveries();
      assertEquals(2, rows.size());
      assertTrue(rows.stream().allMatch(row -> row.submission() == null));
      assertFalse(status.isRollbackOnly(), "Missing work must be a successful read");
      return null;
    });
  }

  @Test
  void includesMissingWorkAndPrivateGradesButNotOtherStudentsOrOtherCourses() {
    var essay = contents.createAssignment(module.getId(), "Essay", 0, "Write", null, false, false, TEACHER);
    var code = contents.createCodingExercise(module.getId(), "Code", 1, "https://example.com/repo", null, false, false, TEACHER);
    contents.createStaticPage(module.getId(), "Page", 2, "Read", null, null, TEACHER);
    var own = submissions.submit(essay.getId(), "Alice work", ALICE);
    submissions.grade(own.getId(), 87, "Private teacher feedback", TEACHER);
    submissions.submit(code.getId(), "Bob secret", BOB);
    var otherCourse = courses.createCourse("Other", "Other", Term.SPRING, 2027, TEACHER);
    var otherModule = modules.createModule(otherCourse.getId(), "Other module", TEACHER);
    contents.createAssignment(otherModule.getId(), "Other essay", 0, "Other", null, false, false, TEACHER);

    var result = overview.getDeliveries(course.getId(), ALICE, TEACHER);
    assertEquals(ALICE, result.studentEmail());
    assertEquals(2, result.deliveries().size());
    var delivered = result.deliveries().get(0);
    assertEquals("Alice work", delivered.submission().text());
    assertEquals(87.0, delivered.submission().grade());
    assertEquals("Private teacher feedback", delivered.submission().feedback());
    assertFalse(delivered.gradesVisibleToStudent());
    assertNull(result.deliveries().get(1).submission());
    verifyNoInteractions(quizGrades);
  }

  @Test
  void sharedFilesAndLateFlagsAppearForEachGroupMember() {
    var assignment = contents.createAssignment(module.getId(), "Group report", 0, "Write",
        LocalDateTime.now(HandInPolicy.ZONE).minusHours(1), true, true, TEACHER);
    var group = groups.createGroup(course.getId(), "Team", TEACHER);
    groups.addMember(group.getId(), ALICE, TEACHER);
    groups.addMember(group.getId(), BOB, TEACHER);
    var submitted = submissions.submit(assignment.getId(), "", new SubmissionFile("report.pdf", "%PDF-1.4".getBytes()), ALICE);
    var alice = overview.getDeliveries(course.getId(), ALICE, TEACHER).deliveries().get(0).submission();
    var bob = overview.getDeliveries(course.getId(), BOB, TEACHER).deliveries().get(0).submission();
    assertEquals(submitted.getId(), alice.id());
    assertEquals(alice.id(), bob.id());
    assertEquals(group.getId(), bob.groupId());
    assertEquals("report.pdf", bob.fileName());
    assertTrue(bob.lateSubmission());
  }

  @Test
  void includesQuizAttemptAndScoreEvenWhenResultsArePrivate() {
    var quiz = contents.createQuiz(module.getId(), "Quiz", 0, 3, null, false, TEACHER);
    QuizGrade grade = new QuizGrade(quiz.getId(), ALICE, 1, 3, 4, .75, List.of(), LocalDateTime.now());
    when(quizGrades.attemptsUsed(quiz, ALICE)).thenReturn(2);
    when(quizGrades.find(quiz, ALICE)).thenReturn(Optional.of(grade));
    when(quizGrades.findLatest(quiz, ALICE)).thenReturn(Optional.of(grade));
    var row = overview.getDeliveries(course.getId(), ALICE, TEACHER).deliveries().get(0);
    assertEquals(.75, row.quiz().bestScore());
    assertEquals(2, row.quiz().attemptsUsed());
    assertEquals(1, row.quiz().latestGradedAttempt());
    assertFalse(row.gradesVisibleToStudent());
    assertFalse(row.availableToStudent(), "Draft quizzes are labelled unavailable");
  }

  @Test
  void rejectsStudentsOutsidersAndTeachersOfAnotherCourse() {
    assertThrows(ForbiddenException.class, () -> overview.getDeliveries(course.getId(), BOB, ALICE));
    users.createUser("other@example.com", "Other teacher");
    courses.createCourse("Other", "Course", Term.SPRING, 2027, "other@example.com");
    assertThrows(ForbiddenException.class, () -> overview.getDeliveries(course.getId(), ALICE, "other@example.com"));
    assertThrows(NotFoundException.class, () -> overview.getDeliveries(course.getId(), TEACHER, TEACHER));
    assertThrows(NotFoundException.class, () -> overview.getDeliveries(course.getId(), "missing@example.com", TEACHER));
  }
}
