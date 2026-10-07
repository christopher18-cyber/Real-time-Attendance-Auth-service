package com.genius.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "attendance_records", uniqueConstraints =
@UniqueConstraint(name = "uq_attendance_session_user", columnNames = {"session_id", "user_id"}))
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String matricNo;

    @Column(nullable = false)
    private String courseCode;

    @Column(name = "session_id", nullable = false)
    private Long sessionId; // Tied to the specific AttendanceSession

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String status;
}