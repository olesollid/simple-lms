package com._gods.canvas2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.UserRepository;

@DataJpaTest
@Import(CourseService.class)
class CourseServiceTests {

  @Autowired
  private CourseService courseService;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void createsAndStoresCourse() {
    Course course = courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026);

    assertEquals(course.getId(), courseService.getCourseById(course.getId()).getId());
  }

  @Test
  void retrievesAllCourses() {
    courseService.createCourse("Course A", "desc", Term.AUTUMN, 2026);
    courseService.createCourse("Course B", "desc", Term.SPRING, 2027);

    List<Course> courses = courseService.getAllCourses();

    assertEquals(2, courses.size());
  }

  @Test
  void retrievesCourseById() {
    Course course = courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026);

    Course found = courseService.getCourseById(course.getId());

    assertEquals("Intro to Testing", found.getTitle());
    assertEquals(Term.AUTUMN, found.getTerm());
    assertEquals(2026, found.getYear());
  }

  @Test
  void updatesExistingCourse() {
    Course course = courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026);

    Course updated = courseService.updateCourse(course.getId(), "Advanced Testing",
        "new desc", Term.SPRING, 2027);

    assertEquals("Advanced Testing", updated.getTitle());
    assertEquals("new desc", updated.getDescription());
    assertEquals(Term.SPRING, updated.getTerm());
    assertEquals(2027, updated.getYear());

    Course reloaded = courseService.getCourseById(course.getId());
    assertEquals("Advanced Testing", reloaded.getTitle());
  }

  @Test
  void rejectsInvalidCourseData() {
    assertThrows(IllegalArgumentException.class,
        () -> courseService.createCourse("  ", "desc", Term.AUTUMN, 2026));
  }

  @Test
  void throwsWhenCourseNotFoundOnGet() {
    assertThrows(CourseNotFoundException.class, () -> courseService.getCourseById(999L));
  }

  @Test
  void throwsWhenCourseNotFoundOnUpdate() {
    assertThrows(CourseNotFoundException.class,
        () -> courseService.updateCourse(999L, "Title", "desc", Term.AUTUMN, 2026));
  }

  @Test
  void throwsWhenCourseNotFoundOnDelete() {
    assertThrows(CourseNotFoundException.class, () -> courseService.deleteCourse(999L));
  }

  @Test
  void deletesCourseWithNoDependents() {
    Course course = courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026);

    courseService.deleteCourse(course.getId());

    assertThrows(CourseNotFoundException.class, () -> courseService.getCourseById(course.getId()));
  }

  @Test
  void rejectsDeletingCourseWithDependents() {
    Course course = courseService.createCourse("Intro to Testing", "desc", Term.AUTUMN, 2026);
    User user = userRepository.save(new User("student@example.com", "Student Name"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));

    assertThrows(CourseHasDependentsException.class, () -> courseService.deleteCourse(course.getId()));

    assertTrue(courseService.getCourseById(course.getId()) != null);
  }
}
