package com._gods.canvas2.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.service.CourseService;
import com._gods.canvas2.web.dto.CourseRequest;
import com._gods.canvas2.web.dto.CourseResponse;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

  private final CourseService courseService;

  public CourseController(CourseService courseService) {
    this.courseService = courseService;
  }

  @PostMapping
  public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest request) {
    Course course = courseService.createCourse(request.title(), request.description(),
        request.term(), request.year());
    return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(course));
  }

  @GetMapping
  public List<CourseResponse> getAllCourses() {
    return courseService.getAllCourses().stream().map(CourseResponse::from).toList();
  }

  @GetMapping("/{id}")
  public CourseResponse getCourseById(@PathVariable Long id) {
    return CourseResponse.from(courseService.getCourseById(id));
  }

  @PutMapping("/{id}")
  public CourseResponse updateCourse(@PathVariable Long id, @RequestBody CourseRequest request) {
    Course course = courseService.updateCourse(id, request.title(), request.description(),
        request.term(), request.year());
    return CourseResponse.from(course);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
    courseService.deleteCourse(id);
    return ResponseEntity.noContent().build();
  }
}
