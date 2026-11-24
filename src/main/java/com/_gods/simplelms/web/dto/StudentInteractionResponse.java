package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

import com._gods.simplelms.model.StudentInteraction;

public record StudentInteractionResponse(Long id, Long contentId, String userEmail,
    LocalDateTime completedAt) {
  public static StudentInteractionResponse from(StudentInteraction interaction) {
    return new StudentInteractionResponse(interaction.getId(), interaction.getContent().getId(),
        interaction.getUser().getEmail(), interaction.getCompletedAt());
  }
}
