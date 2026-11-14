package com._gods.canvas2.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.Hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "modules")
public class Module {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false)
  private String title;

  @ManyToMany
  @JoinTable(
      name = "module_dependencies",
      joinColumns = @JoinColumn(name = "module_id"),
      inverseJoinColumns = @JoinColumn(name = "depends_on_module_id"))
  private Set<Module> dependencies = new HashSet<>();

  protected Module() {
  }

  public Module(Course course, String title) {
    if (course == null) {
      throw new IllegalArgumentException("Course must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }

    this.course = course;
    this.title = title.trim();
  }

  public Long getId() {
    return id;
  }

  public Course getCourse() {
    return course;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    this.title = title.trim();
  }

  public Set<Module> getDependencies() {
    return Collections.unmodifiableSet(dependencies);
  }

  /**
   * Forces the lazy dependency collection to load now, while a Hibernate session is still open.
   * Needed before returning this entity to a caller outside the current transaction, since
   * {@link #getDependencies()} wraps the collection in {@code Collections.unmodifiableSet},
   * which {@link Hibernate#initialize} cannot see through to find the underlying lazy proxy.
   */
  public void initializeDependencies() {
    Hibernate.initialize(dependencies);
  }

  public void addDependency(Module other) {
    if (other == null) {
      throw new IllegalArgumentException("Dependency must not be null");
    }
    if (other.equals(this)) {
      throw new IllegalArgumentException("A module cannot depend on itself");
    }
    if (!sameCourse(other.getCourse(), this.course)) {
      throw new IllegalArgumentException("Dependency must belong to the same course");
    }
    if (createsCycle(other)) {
      throw new IllegalArgumentException("Adding this dependency would create a cycle");
    }
    dependencies.add(other);
  }

  public void removeDependency(Module other) {
    dependencies.remove(other);
  }

  /**
   * Same course if it's literally the same object (unpersisted entities, e.g. in unit tests) or
   * the same persisted row (by id) -- plain reference equality fails once two Module instances
   * hold separately-loaded Course objects for the same row, which happens outside a single
   * Hibernate session/transaction.
   */
  private static boolean sameCourse(Course a, Course b) {
    if (a == b) {
      return true;
    }
    return a.getId() != null && a.getId().equals(b.getId());
  }

  private boolean createsCycle(Module candidate) {
    // Adding `this -> candidate` creates a cycle if `this` is already reachable from `candidate`.
    Set<Module> visited = new HashSet<>();
    return isReachable(candidate, this, visited);
  }

  private boolean isReachable(Module from, Module target, Set<Module> visited) {
    if (!visited.add(from)) {
      return false;
    }
    if (from.getDependencies().contains(target)) {
      return true;
    }
    for (Module dependency : from.getDependencies()) {
      if (isReachable(dependency, target, visited)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Module module)) {
      return false;
    }
    return id != null && id.equals(module.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
