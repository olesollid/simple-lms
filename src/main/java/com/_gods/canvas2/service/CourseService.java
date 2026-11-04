package com._gods.canvas2.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.repository.EnrollmentRepository;
import com._gods.canvas2.repository.ModuleRepository;

@Service
public class CourseService {

  private final CourseRepository courseRepository;
  private final ModuleRepository moduleRepository;
  private final EnrollmentRepository enrollmentRepository;

  public CourseService(CourseRepository courseRepository, ModuleRepository moduleRepository,
      EnrollmentRepository enrollmentRepository) {
    this.courseRepository = courseRepository;
    this.moduleRepository = moduleRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  public Course createCourse(String title, String description, Term term, int year) {
    return courseRepository.save(new Course(title, description, term, year));
  }

  public List<Course> getAllCourses() {
    return courseRepository.findAll();
  }

  public Course getCourseById(Long id) {
    return courseRepository.findById(id)
        .orElseThrow(() -> new CourseNotFoundException(id));
  }

  public Course updateCourse(Long id, String title, String description, Term term, int year) {
    Course course = getCourseById(id);
    course.setTitle(title);
    course.setDescription(description);
    course.setTerm(term);
    course.setYear(year);
    return courseRepository.save(course);
  }

  public void deleteCourse(Long id) {
    Course course = getCourseById(id);
    boolean hasModules = !moduleRepository.findByCourse(course).isEmpty();
    boolean hasEnrollments = !enrollmentRepository.findByCourse(course).isEmpty();
    if (hasModules || hasEnrollments) {
      throw new CourseHasDependentsException(id);
    }
    courseRepository.delete(course);
  }
}
