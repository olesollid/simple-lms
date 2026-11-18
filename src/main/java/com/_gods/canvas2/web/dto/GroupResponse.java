package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.Group;

public record GroupResponse(Long id, Long courseId, String name) {

  public static GroupResponse from(Group group) {
    return new GroupResponse(group.getId(), group.getCourse().getId(), group.getName());
  }
}
