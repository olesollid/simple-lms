package com._gods.canvas2.web.dto;

import java.util.List;

/** A new order for a list of items: every item's id exactly once, first to last. */
public record OrderRequest(List<Long> ids) {
}
