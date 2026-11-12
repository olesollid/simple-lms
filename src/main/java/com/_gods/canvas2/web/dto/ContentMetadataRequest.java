package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

public record ContentMetadataRequest(String title, int position, LocalDateTime visibleFrom,
    LocalDateTime visibleUntil) {
}
