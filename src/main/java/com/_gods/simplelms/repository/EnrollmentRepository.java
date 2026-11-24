package com._gods.simplelms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.User;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
  List<Enrollment> findByCourse(Course course);

  List<Enrollment> findByCourseAndRole(Course course, Role role);

  List<Enrollment> findByUser(User user);

  Optional<Enrollment> findByUserAndCourse(User user, Course course);

  boolean existsByUser(User user);
}
