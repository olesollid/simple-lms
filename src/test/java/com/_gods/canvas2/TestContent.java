package com._gods.canvas2;

import java.time.LocalDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("TEST")
class TestContent extends Content {

  protected TestContent() {
    super();
  }

  TestContent(Module module, String title, int position) {
    super(module, title, position);
  }

  TestContent(Module module, String title, int position, LocalDateTime visibleFrom,
      LocalDateTime visibleUntil) {
    super(module, title, position, visibleFrom, visibleUntil);
  }
}
