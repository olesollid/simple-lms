package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

public record ContentMetadataRequest(String title, int position, LocalDateTime visibleFrom,
    LocalDateTime visibleUntil) {
}
