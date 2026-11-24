package com._gods.simplelms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
