package com._gods.canvas2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {
  List<Group> findByCourse(Course course);
}
