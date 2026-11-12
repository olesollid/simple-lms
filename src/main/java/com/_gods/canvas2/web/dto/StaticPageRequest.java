package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

public record StaticPageRequest(String title, int position, String body,
    LocalDateTime visibleFrom, LocalDateTime visibleUntil) {
}
