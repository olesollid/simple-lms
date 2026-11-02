package com._gods.canvas2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Module;

public interface ContentRepository extends JpaRepository<Content, Long> {
  List<Content> findByModuleOrderByPosition(Module module);
}
