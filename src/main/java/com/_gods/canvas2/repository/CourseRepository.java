package com._gods.canvas2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
