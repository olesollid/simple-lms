package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;
import com._gods.canvas2.repository.UserRepository;
import com._gods.canvas2.service.ContentNotFoundException;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.ModuleService;
import com._gods.canvas2.service.StudentInteractionService;
import com._gods.canvas2.service.UserService;

@DataJpaTest
@Import({ContentService.class, ModuleService.class, EnrollmentService.class, UserService.class,
    StudentInteractionService.class})
class TutorAccessServiceTests {

  @Autowired private ContentService contentService;
  @Autowired private UserService userService;
  @Autowired private CourseRepository courseRepository;
  @Autowired private ModuleRepository moduleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;

  private static int counter = 0;

  private Course course(String title) {
    return courseRepository.save(new Course(title, "desc", Term.AUTUMN, 2026));
  }

  private String member(Course course, Role role) {
    String email = role.name().toLowerCase() + (counter++) + "@example.com";
    User user = userRepository.save(new User(email, role.name()));
    enrollmentRepository.save(new Enrollment(user, course, role));
    return email;
  }

  private String enrolled(Course course, String email, Role role) {
    User user = userRepository.findById(email).orElseThrow();
    enrollmentRepository.save(new Enrollment(user, course, role));
    return email;
  }

  private Assignment assignmentIn(Course course, String teacher, String title) {
    Module module = moduleRepository.save(new Module(course, "Week " + (counter++)));
    return contentService.createAssignment(module.getId(), title, 0, "Do the thing", null,
        false, false, teacher);
  }

  private TutorAccessService service(boolean enabled, Course... enabledCourses) {
    Set<Long> ids = new java.util.HashSet<>();
    for (Course enabledCourse : enabledCourses) {
      ids.add(enabledCourse.getId());
    }
    AiTutorProperties properties = new AiTutorProperties(enabled, ids,
        new AiTutorProperties.RateLimit(20, java.time.Duration.ofMinutes(1)));
    return new TutorAccessService(properties, enrollmentRepository, courseRepository, userService,
        contentService);
  }

  private static TutorContextRequest context(TutorPageType type, Long courseId, Long assignmentId) {
    return new TutorContextRequest(type, courseId, assignmentId);
  }

  // ---- config: is the tutor on for this student? ----

  @Test
  void isOffByDefaultEvenForAnEnrolledStudent() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertFalse(service(false, course).isEnabledFor(student, course.getId()));
    assertFalse(service(false, course).isEnabledFor(student, null));
  }

  @Test
  void isOnForAnEnrolledStudentOfAnEnabledCourse() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertTrue(service(true, course).isEnabledFor(student, course.getId()));
  }

  @Test
  void isOffForACourseThatIsNotOptedIn() {
    Course enabled = course("DAT250");
    Course other = course("INF100");
    String student = member(other, Role.STUDENT);

    assertFalse(service(true, enabled).isEnabledFor(student, other.getId()));
  }

  @Test
  void isOffForTeachersAndForPeopleNotEnrolled() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    String outsider = member(course("INF100"), Role.STUDENT);

    TutorAccessService service = service(true, course);

    assertFalse(service.isEnabledFor(teacher, course.getId()));
    assertFalse(service.isEnabledFor(outsider, course.getId()));
    assertFalse(service.isEnabledFor("nobody@example.com", course.getId()));
  }

  @Test
  void isOffForAnUnknownCourseId() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertFalse(service(true, course).isEnabledFor(student, 99999L));
  }

  @Test
  void dashboardNeedsAStudentEnrollmentInAnOptedInCourse() {
    Course enabled = course("DAT250");
    Course other = course("INF100");
    String inEnabled = member(enabled, Role.STUDENT);
    String inOther = member(other, Role.STUDENT);
    String teacherOnly = member(enabled, Role.TEACHER);

    TutorAccessService service = service(true, enabled);

    assertTrue(service.isEnabledFor(inEnabled, null));
    assertFalse(service.isEnabledFor(inOther, null));
    assertFalse(service.isEnabledFor(teacherOnly, null));
  }

  @Test
  void rolesAreCourseSpecific() {
    Course taught = course("DAT250");
    Course attended = course("INF100");
    String person = member(taught, Role.TEACHER);
    enrolled(attended, person, Role.STUDENT);

    TutorAccessService service = service(true, taught, attended);

    assertFalse(service.isEnabledFor(person, taught.getId()));
    assertTrue(service.isEnabledFor(person, attended.getId()));
  }

  // ---- verify: the permission checks every chat request goes through ----

  @Test
  void rejectsACourseIdTheStudentIsNotEnrolledIn() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);

    // Even when the tutor is opted in for that course, an outsider is refused.
    TutorAccessService service = service(true, mine, theirs);

    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, theirs.getId(), null)));
    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.ASSIGNMENT, theirs.getId(), 1L)));
  }

  @Test
  void refusesAnUnknownAndAForbiddenCourseInTheSameWay() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);
    TutorAccessService service = service(true, mine, theirs);

    ForbiddenException forbidden = assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, theirs.getId(), null)));
    ForbiddenException unknown = assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, 99999L, null)));

    assertEquals(forbidden.getMessage(), unknown.getMessage());
  }

  @Test
  void rejectsEverythingWhileTheGlobalSwitchIsOff() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);
    TutorAccessService service = service(false, course);

    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, course.getId(), null)));
    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.DASHBOARD, null, null)));
  }

  @Test
  void rejectsACourseThatIsNotOptedIn() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertThrows(ForbiddenException.class,
        () -> service(true).verify(student, context(TutorPageType.COURSE, course.getId(), null)));
  }

  @Test
  void rejectsTeachersAndUnknownUsers() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    TutorAccessService service = service(true, course);

    assertThrows(ForbiddenException.class,
        () -> service.verify(teacher, context(TutorPageType.COURSE, course.getId(), null)));
    assertThrows(ForbiddenException.class,
        () -> service.verify("ghost@example.com", context(TutorPageType.COURSE, course.getId(), null)));
  }

  @Test
  void verifiesACoursePageAndReturnsTheNameFromTheDatabase() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    VerifiedTutorContext verified = service(true, course)
        .verify(student, context(TutorPageType.COURSE, course.getId(), null));

    assertEquals(TutorPageType.COURSE, verified.pageType());
    assertEquals(course.getId(), verified.courseId());
    assertEquals("DAT250", verified.courseName());
    assertNull(verified.assignmentId());
  }

  @Test
  void verifiesAnAssignmentPageAndReturnsBothNames() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Assignment lab = assignmentIn(course, teacher, "Lab 3");

    VerifiedTutorContext verified = service(true, course)
        .verify(student, context(TutorPageType.ASSIGNMENT, course.getId(), lab.getId()));

    assertEquals("Lab 3", verified.assignmentTitle());
    assertEquals("DAT250", verified.courseName());
    assertEquals(lab.getId(), verified.assignmentId());
  }

  @Test
  void rejectsAnAssignmentFromAnotherCourse() {
    Course mine = course("DAT250");
    Course other = course("INF100");
    String teacher = member(other, Role.TEACHER);
    String student = member(mine, Role.STUDENT);
    Assignment foreign = assignmentIn(other, teacher, "Foreign lab");

    TutorAccessService service = service(true, mine, other);

    // The student is not in INF100 at all.
    assertThrows(ContentNotFoundException.class, () -> service.verify(student,
        context(TutorPageType.ASSIGNMENT, mine.getId(), foreign.getId())));
  }

  @Test
  void rejectsAnAssignmentThatBelongsToADifferentCourseThanClaimed() {
    Course first = course("DAT250");
    Course second = course("INF100");
    String teacher = member(second, Role.TEACHER);
    String student = member(first, Role.STUDENT);
    enrolled(second, student, Role.STUDENT);
    Assignment inSecond = assignmentIn(second, teacher, "Lab in second");

    // Enrolled in both courses, but the assignment is not in the course the request names.
    assertThrows(ContentNotFoundException.class, () -> service(true, first, second).verify(student,
        context(TutorPageType.ASSIGNMENT, first.getId(), inSecond.getId())));
  }

  @Test
  void rejectsAnAssignmentThatIsNotYetVisible() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Assignment hidden = assignmentIn(course, teacher, "Next week's lab");
    contentService.updateContentMetadata(hidden.getId(), "Next week's lab", 0,
        LocalDateTime.now().plusDays(7), null, teacher);

    assertThrows(ContentNotFoundException.class, () -> service(true, course).verify(student,
        context(TutorPageType.ASSIGNMENT, course.getId(), hidden.getId())));
  }

  @Test
  void rejectsContentThatIsNotAnAssignment() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module module = moduleRepository.save(new Module(course, "Week 1"));
    Content page = contentService.createStaticPage(module.getId(), "Syllabus", 0, "text", null,
        null, teacher);
    // A static page is real content in the right course, but it is not an assignment.

    assertThrows(ContentNotFoundException.class, () -> service(true, course).verify(student,
        context(TutorPageType.ASSIGNMENT, course.getId(), page.getId())));
  }

  @Test
  void rejectsAnAssignmentIdThatDoesNotExist() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertThrows(ContentNotFoundException.class, () -> service(true, course).verify(student,
        context(TutorPageType.ASSIGNMENT, course.getId(), 99999L)));
  }

  @Test
  void ignoresIdsSentWithADashboardOrOtherPage() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);
    TutorAccessService service = service(true, mine);

    VerifiedTutorContext dashboard = service.verify(student,
        context(TutorPageType.DASHBOARD, theirs.getId(), 5L));
    VerifiedTutorContext other = service.verify(student,
        context(TutorPageType.OTHER, theirs.getId(), 5L));

    assertNull(dashboard.courseId());
    assertNull(dashboard.courseName());
    assertNull(other.courseId());
  }

  @Test
  void rejectsAMalformedContext() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);
    TutorAccessService service = service(true, course);

    assertThrows(IllegalArgumentException.class, () -> service.verify(student, null));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(student, context(null, course.getId(), null)));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, null, null)));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(student, context(TutorPageType.ASSIGNMENT, course.getId(), null)));
  }
}
