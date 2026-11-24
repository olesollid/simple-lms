package com._gods.simplelms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {
  List<Group> findByCourse(Course course);
}
