package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

@Entity
@DiscriminatorValue("STATIC_PAGE")
public class StaticPage extends Content {

  @Lob
  @Column(nullable = false)
  private String body;

  protected StaticPage() {
    super();
  }

  public StaticPage(Module module, String title, int position, String body) {
    this(module, title, position, body, null, null);
  }

  public StaticPage(Module module, String title, int position, String body, LocalDateTime visibleFrom,
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
