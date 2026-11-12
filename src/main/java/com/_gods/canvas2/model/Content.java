package com._gods.canvas2.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "contents")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "content_type")
public abstract class Content {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "module_id", nullable = false)
  private Module module;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private int position;

  private LocalDateTime visibleFrom;

  private LocalDateTime visibleUntil;

  protected Content() {
  }

  protected Content(Module module, String title, int position) {
    this(module, title, position, null, null);
  }

  protected Content(Module module, String title, int position, LocalDateTime visibleFrom,
      LocalDateTime visibleUntil) {
    if (module == null) {
      throw new IllegalArgumentException("Module must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    if (position < 0) {
      throw new IllegalArgumentException("Position must not be negative");
    }
    if (visibleFrom != null && visibleUntil != null && !visibleUntil.isAfter(visibleFrom)) {
      throw new IllegalArgumentException("visibleUntil must be after visibleFrom");
    }

    this.module = module;
    this.title = title.trim();
    this.position = position;
    this.visibleFrom = visibleFrom;
    this.visibleUntil = visibleUntil;
  }

  public Long getId() {
    return id;
  }

  public Module getModule() {
    return module;
  }

  public String getTitle() {
    return title;
  }

  public int getPosition() {
    return position;
  }

  public LocalDateTime getVisibleFrom() {
    return visibleFrom;
  }

  public LocalDateTime getVisibleUntil() {
    return visibleUntil;
  }

  public boolean isVisibleAt(LocalDateTime time) {
    if (visibleFrom != null && time.isBefore(visibleFrom)) {
      return false;
    }
    if (visibleUntil != null && !time.isBefore(visibleUntil)) {
      return false;
    }
    return true;
  }

  public void updateMetadata(String title, int position, LocalDateTime visibleFrom,
      LocalDateTime visibleUntil) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    if (position < 0) {
      throw new IllegalArgumentException("Position must not be negative");
    }
    if (visibleFrom != null && visibleUntil != null && !visibleUntil.isAfter(visibleFrom)) {
      throw new IllegalArgumentException("visibleUntil must be after visibleFrom");
    }

    this.title = title.trim();
    this.position = position;
    this.visibleFrom = visibleFrom;
    this.visibleUntil = visibleUntil;
  }
}
