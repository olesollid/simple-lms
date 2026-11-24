package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

public record StaticPageRequest(String title, int position, String body,
    LocalDateTime visibleFrom, LocalDateTime visibleUntil) {
}
