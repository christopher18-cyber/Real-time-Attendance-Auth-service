package com.genius.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "courses")
@Data
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String courseCode; // e.g. CSC301

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String semester; // e.g., "First Semester 2026/2027"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(nullable = false)
    private Long lecturerId;

    private String room;
    private String schedule;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "course_supporting_lecturers", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "email", nullable = false)
    private Set<String> supportingLecturerEmails = new HashSet<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CourseStatus {
        DRAFT, ACTIVE
    }
}
