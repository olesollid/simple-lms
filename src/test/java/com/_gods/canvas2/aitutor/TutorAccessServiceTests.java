package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;
import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.service.ForbiddenException;
import com._gods.canvas2.service.UserService;

@DataJpaTest
@Import({EnrollmentService.class, UserService.class})
class TutorAccessServiceTests {

  @Autowired private UserService userService;
  @Autowired private CourseRepository courseRepository;
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

  private TutorAccessService service(boolean enabled) {
    AiTutorProperties properties = new AiTutorProperties(enabled,
        new AiTutorProperties.RateLimit(20, java.time.Duration.ofMinutes(1)));
    return new TutorAccessService(properties, enrollmentRepository, courseRepository, userService);
  }

  private static TutorContextRequest context(TutorPageType type, Long courseId) {
    return new TutorContextRequest(type, courseId);
  }

  // ---- config: is the tutor on for this student? ----

  @Test
  void isOffByDefaultEvenForAnEnrolledStudent() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertFalse(service(false).isEnabledFor(student, course.getId()));
    assertFalse(service(false).isEnabledFor(student, null));
  }

  @Test
  void isOnForAnEnrolledStudentOfAnEnabledCourse() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertTrue(service(true).isEnabledFor(student, course.getId()));
  }

  @Test
  void isOffForTeachersAndForPeopleNotEnrolled() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    String outsider = member(course("INF100"), Role.STUDENT);

    TutorAccessService service = service(true);

    assertFalse(service.isEnabledFor(teacher, course.getId()));
    assertFalse(service.isEnabledFor(outsider, course.getId()));
    assertFalse(service.isEnabledFor("nobody@example.com", course.getId()));
  }

  @Test
  void isOffForAnUnknownCourseId() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    assertFalse(service(true).isEnabledFor(student, 99999L));
  }

  @Test
  void dashboardNeedsAStudentEnrollmentInSomeCourse() {
    Course first = course("DAT250");
    Course second = course("INF100");
    String student = member(first, Role.STUDENT);
    String teacherOnly = member(second, Role.TEACHER);

    TutorAccessService service = service(true);

    assertTrue(service.isEnabledFor(student, null));
    assertFalse(service.isEnabledFor(teacherOnly, null));
    assertFalse(service.isEnabledFor("nobody@example.com", null));
  }

  @Test
  void everyCourseIsAvailableToItsStudentsWithNothingToConfigure() {
    Course first = course("DAT250");
    Course second = course("INF100");
    String inFirst = member(first, Role.STUDENT);
    String inSecond = member(second, Role.STUDENT);

    TutorAccessService service = service(true);

    assertTrue(service.isEnabledFor(inFirst, first.getId()));
    assertTrue(service.isEnabledFor(inSecond, second.getId()));
    assertEquals("DAT250", service.verify(inFirst, context(TutorPageType.COURSE, first.getId())).courseName());
    assertEquals("INF100", service.verify(inSecond, context(TutorPageType.COURSE, second.getId())).courseName());
    // Being in one course does not open another.
    assertFalse(service.isEnabledFor(inFirst, second.getId()));
  }

  @Test
  void rolesAreCourseSpecific() {
    Course taught = course("DAT250");
    Course attended = course("INF100");
    String person = member(taught, Role.TEACHER);
    enrolled(attended, person, Role.STUDENT);

    TutorAccessService service = service(true);

    assertFalse(service.isEnabledFor(person, taught.getId()));
    assertTrue(service.isEnabledFor(person, attended.getId()));
  }

  // ---- verify: the permission checks every chat request goes through ----

  @Test
  void rejectsACourseIdTheStudentIsNotEnrolledIn() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);

    // The tutor is on for every course, but an outsider is still refused.
    TutorAccessService service = service(true);

    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, theirs.getId())));
  }

  @Test
  void refusesAnUnknownAndAForbiddenCourseInTheSameWay() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);
    TutorAccessService service = service(true);

    ForbiddenException forbidden = assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, theirs.getId())));
    ForbiddenException unknown = assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, 99999L)));

    assertEquals(forbidden.getMessage(), unknown.getMessage());
  }

  @Test
  void rejectsEverythingWhileTheGlobalSwitchIsOff() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);
    TutorAccessService service = service(false);

    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, course.getId())));
    assertThrows(ForbiddenException.class,
        () -> service.verify(student, context(TutorPageType.DASHBOARD, null)));
  }

  @Test
  void rejectsTeachersAndUnknownUsers() {
    Course course = course("DAT250");
    String teacher = member(course, Role.TEACHER);
    TutorAccessService service = service(true);

    assertThrows(ForbiddenException.class,
        () -> service.verify(teacher, context(TutorPageType.COURSE, course.getId())));
    assertThrows(ForbiddenException.class,
        () -> service.verify("ghost@example.com", context(TutorPageType.COURSE, course.getId())));
  }

  @Test
  void verifiesACoursePageAndReturnsTheNameFromTheDatabase() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);

    VerifiedTutorContext verified = service(true)
        .verify(student, context(TutorPageType.COURSE, course.getId()));

    assertEquals(TutorPageType.COURSE, verified.pageType());
    assertEquals(course.getId(), verified.courseId());
    assertEquals("DAT250", verified.courseName());
      }

  @Test
  void ignoresIdsSentWithADashboardOrOtherPage() {
    Course mine = course("DAT250");
    Course theirs = course("SECRET101");
    String student = member(mine, Role.STUDENT);
    TutorAccessService service = service(true);

    VerifiedTutorContext dashboard = service.verify(student,
        context(TutorPageType.DASHBOARD, theirs.getId()));
    VerifiedTutorContext other = service.verify(student,
        context(TutorPageType.OTHER, theirs.getId()));

    assertNull(dashboard.courseId());
    assertNull(dashboard.courseName());
    assertNull(other.courseId());
  }

  @Test
  void rejectsAMalformedContext() {
    Course course = course("DAT250");
    String student = member(course, Role.STUDENT);
    TutorAccessService service = service(true);

    assertThrows(IllegalArgumentException.class, () -> service.verify(student, null));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(student, context(null, course.getId())));
    assertThrows(IllegalArgumentException.class,
        () -> service.verify(student, context(TutorPageType.COURSE, null)));
  }

}
