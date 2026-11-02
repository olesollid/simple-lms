package com._gods.canvas2;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

@Entity
@DiscriminatorValue("PAGE")
public class Page extends Content {

  @Lob
  @Column(nullable = false)
  private String body;

  protected Page() {
    super();
  }

  public Page(Module module, String title, int position, String body) {
    this(module, title, position, body, null, null);
  }

  public Page(Module module, String title, int position, String body, LocalDateTime visibleFrom,
      LocalDateTime visibleUntil) {
    super(module, title, position, visibleFrom, visibleUntil);

    if (body == null) {
      throw new IllegalArgumentException("Body must not be null");
    }

    this.body = body;
  }

  public String getBody() {
    return body;
  }
}
