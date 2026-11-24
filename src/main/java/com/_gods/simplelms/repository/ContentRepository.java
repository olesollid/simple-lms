package com._gods.simplelms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Content;
import com._gods.simplelms.model.Module;

public interface ContentRepository extends JpaRepository<Content, Long> {
  List<Content> findByModuleOrderByPosition(Module module);
}
