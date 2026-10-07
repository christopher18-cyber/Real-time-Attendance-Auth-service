package com.genius.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "legacy_attendance_records", indexes = {
        @Index(name = "idx_session_student", columnList = "sessionId, studentId", unique = true)
})
@Data
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private String matricNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    private LocalDateTime markedAt = LocalDateTime.now();

    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE
    }
}
