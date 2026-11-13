package com._gods.canvas2.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
@Transactional
public class CourseService {

  private final CourseRepository courseRepository;
  private final ModuleRepository moduleRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final EnrollmentService enrollmentService;

  public CourseService(CourseRepository courseRepository, ModuleRepository moduleRepository,
      EnrollmentRepository enrollmentRepository, EnrollmentService enrollmentService) {
    this.courseRepository = courseRepository;
    this.moduleRepository = moduleRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.enrollmentService = enrollmentService;
  }

  /** The creator is automatically enrolled as TEACHER, since that's the only way to legitimately become one. */
  public Course createCourse(String title, String description, Term term, int year,
      String requesterEmail) {
    Course course = courseRepository.save(new Course(title, description, term, year));
    enrollmentService.enrollWithoutAuthorization(course.getId(), requesterEmail, Role.TEACHER);
    return course;
  }

  /** Not a global catalog: only the courses the requester is enrolled in. */
  @Transactional(readOnly = true)
  public List<Course> getMyCourses(String requesterEmail) {
    return enrollmentService.getEnrollmentsByUser(requesterEmail, requesterEmail).stream()
        .map(Enrollment::getCourse)
        .distinct()
        .toList();
  }

  @Transactional(readOnly = true)
  public Course getCourseById(Long id, String requesterEmail) {
    enrollmentService.requireEnrolled(id, requesterEmail);
    return getCourse(id);
  }

  private Course getCourse(Long id) {
    return courseRepository.findById(id)
        .orElseThrow(() -> new CourseNotFoundException(id));
  }

  public Course updateCourse(Long id, String title, String description, Term term, int year,
      String requesterEmail) {
    requireTeacher(id, requesterEmail);
    Course course = getCourse(id);
    course.setTitle(title);
    course.setDescription(description);
    course.setTerm(term);
    course.setYear(year);
    return courseRepository.save(course);
  }

  public void deleteCourse(Long id, String requesterEmail) {
    requireTeacher(id, requesterEmail);
    Course course = getCourse(id);
    boolean hasModules = !moduleRepository.findByCourse(course).isEmpty();
    // Creating a course auto-enrolls the creator as TEACHER, so their own enrollment doesn't
    // count as a dependent -- otherwise a course could never be deleted by anyone.
    boolean hasOtherEnrollments = enrollmentRepository.findByCourse(course).stream()
        .anyMatch(enrollment -> !enrollment.getUser().getEmail()
            .equals(User.normalizeEmail(requesterEmail)));
    if (hasModules || hasOtherEnrollments) {
      throw new CourseHasDependentsException(id);
    }
    courseRepository.delete(course);
  }

  private void requireTeacher(Long courseId, String requesterEmail) {
    if (!enrollmentService.hasRole(courseId, requesterEmail, Role.TEACHER)) {
      throw new ForbiddenException(
          "Only a teacher enrolled in course " + courseId + " may perform this action");
    }
  }
}
