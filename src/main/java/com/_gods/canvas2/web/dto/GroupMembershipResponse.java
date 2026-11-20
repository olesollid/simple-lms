package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

import com._gods.canvas2.model.GroupMembership;

public record GroupMembershipResponse(Long id, Long groupId, String email, LocalDateTime joinedAt) {

  public static GroupMembershipResponse from(GroupMembership membership) {
    return new GroupMembershipResponse(membership.getId(), membership.getGroup().getId(),
        membership.getUser().getEmail(), membership.getJoinedAt());
  }
}
