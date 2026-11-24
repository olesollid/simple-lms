package com._gods.simplelms.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.service.CourseService;
import com._gods.simplelms.web.dto.CourseRequest;
import com._gods.simplelms.web.dto.CourseResponse;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

  private final CourseService courseService;

  public CourseController(CourseService courseService) {
    this.courseService = courseService;
  }

  @PostMapping
  public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest request,
      Authentication authentication) {
    Course course = courseService.createCourse(request.title(), request.description(),
        request.term(), request.year(), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(course));
  }

  @GetMapping
  public List<CourseResponse> getMyCourses(Authentication authentication) {
    return courseService.getMyCourses(authentication.getName()).stream()
        .map(CourseResponse::from).toList();
  }

  @GetMapping("/{id}")
  public CourseResponse getCourseById(@PathVariable Long id, Authentication authentication) {
    return CourseResponse.from(courseService.getCourseById(id, authentication.getName()));
  }

  @PutMapping("/{id}")
  public CourseResponse updateCourse(@PathVariable Long id, @RequestBody CourseRequest request,
      Authentication authentication) {
    Course course = courseService.updateCourse(id, request.title(), request.description(),
        request.term(), request.year(), authentication.getName());
    return CourseResponse.from(course);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCourse(@PathVariable Long id, Authentication authentication) {
    courseService.deleteCourse(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
