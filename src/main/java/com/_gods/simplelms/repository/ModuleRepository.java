package com._gods.simplelms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;

public interface ModuleRepository extends JpaRepository<Module, Long> {
  List<Module> findByCourse(Course course);

  List<Module> findByCourseOrderByPositionAscIdAsc(Course course);
}
