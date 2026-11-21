package com._gods.canvas2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;

public interface ModuleRepository extends JpaRepository<Module, Long> {
  List<Module> findByCourse(Course course);

  List<Module> findByCourseOrderByPositionAscIdAsc(Course course);
}
