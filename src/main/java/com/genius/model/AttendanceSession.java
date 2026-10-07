package com.genius.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String sessionCode; // e.g., a short unique code for the session

    @Column(nullable = false)
    private Long lecturerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime expiresAt; // Optional timer

    // Add these fields for location-based check-in geofencing
    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String rosterSnapshotJson;

    public enum SessionStatus {
        ACTIVE, CLOSED
    }
}
