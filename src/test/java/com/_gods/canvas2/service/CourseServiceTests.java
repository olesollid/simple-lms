package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import({CourseService.class, EnrollmentService.class, UserService.class})
class CourseServiceTests {

  @Autowired
  private CourseService courseService;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private UserRepository userRepository;

  private static final String TEACHER_EMAIL = "teacher@example.com";

  @BeforeEach
  void seedTeacher() {
    userRepository.save(new User(TEACHER_EMAIL, "Teacher Name"));
  }

  private Course createCourseAsTeacher() {
    return courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026, TEACHER_EMAIL);
  }

  @Test
  void createsAndStoresCourse() {
    Course course = createCourseAsTeacher();

    assertEquals(course.getId(), courseService.getCourseById(course.getId()).getId());
  }

  @Test
  void createCourseEnrollsCreatorAsTeacher() {
    Course course = createCourseAsTeacher();

    assertEquals(Role.TEACHER,
        enrollmentRepository.findByCourse(course).get(0).getRole());
  }

  @Test
  void retrievesAllCourses() {
    courseService.createCourse("Course A", "desc", Term.AUTUMN, 2026, TEACHER_EMAIL);
    courseService.createCourse("Course B", "desc", Term.SPRING, 2027, TEACHER_EMAIL);

    List<Course> courses = courseService.getAllCourses();

    assertEquals(2, courses.size());
  }

  @Test
  void retrievesCourseById() {
    Course course = createCourseAsTeacher();

    Course found = courseService.getCourseById(course.getId());

    assertEquals("Intro to Testing", found.getTitle());
    assertEquals(Term.AUTUMN, found.getTerm());
    assertEquals(2026, found.getYear());
  }

  @Test
  void updatesExistingCourse() {
    Course course = createCourseAsTeacher();

    Course updated = courseService.updateCourse(course.getId(), "Advanced Testing",
        "new desc", Term.SPRING, 2027, TEACHER_EMAIL);

    assertEquals("Advanced Testing", updated.getTitle());
    assertEquals("new desc", updated.getDescription());
    assertEquals(Term.SPRING, updated.getTerm());
    assertEquals(2027, updated.getYear());

    Course reloaded = courseService.getCourseById(course.getId());
    assertEquals("Advanced Testing", reloaded.getTitle());
  }

  @Test
  void rejectsUpdateByNonTeacher() {
    Course course = createCourseAsTeacher();

    assertThrows(ForbiddenException.class, () -> courseService.updateCourse(course.getId(),
        "Advanced Testing", "new desc", Term.SPRING, 2027, "student@example.com"));
  }

  @Test
  void rejectsInvalidCourseData() {
    assertThrows(IllegalArgumentException.class,
        () -> courseService.createCourse("  ", "desc", Term.AUTUMN, 2026, TEACHER_EMAIL));
  }

  @Test
  void throwsWhenCourseNotFoundOnGet() {
    assertThrows(CourseNotFoundException.class, () -> courseService.getCourseById(999L));
  }

  @Test
  void throwsWhenCourseNotFoundOnUpdate() {
    assertThrows(CourseNotFoundException.class,
        () -> courseService.updateCourse(999L, "Title", "desc", Term.AUTUMN, 2026,
            "stranger@example.com"));
  }

  @Test
  void throwsWhenCourseNotFoundOnDelete() {
    assertThrows(CourseNotFoundException.class,
        () -> courseService.deleteCourse(999L, "stranger@example.com"));
  }

  @Test
  void deletesCourseWithNoDependents() {
    Course course = createCourseAsTeacher();

    courseService.deleteCourse(course.getId(), TEACHER_EMAIL);

    assertThrows(CourseNotFoundException.class, () -> courseService.getCourseById(course.getId()));
  }

  @Test
  void rejectsDeletingCourseWithDependents() {
    Course course = createCourseAsTeacher();
    User user = userRepository.save(new User("student@example.com", "Student Name"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));

    assertThrows(CourseHasDependentsException.class,
        () -> courseService.deleteCourse(course.getId(), TEACHER_EMAIL));

    assertTrue(courseService.getCourseById(course.getId()) != null);
  }
}
