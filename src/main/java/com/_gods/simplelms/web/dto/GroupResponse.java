package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.Group;

public record GroupResponse(Long id, Long courseId, String name) {

  public static GroupResponse from(Group group) {
    return new GroupResponse(group.getId(), group.getCourse().getId(), group.getName());
  }
}
