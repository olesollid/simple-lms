package com._gods.canvas2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String name;

    protected Group() {
    }

    public Group(Course course, String name) {
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }

        this.course = course;
        this.name = name.trim();
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getName() {
        return name;
    }
}
