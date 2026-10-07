package com.genius.repo;

import com.genius.model.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findBySessionCode(String sessionCode);
    List<AttendanceSession> findByCourseCode(String courseCode);
    List<AttendanceSession> findByStatusAndExpiresAtAfter(AttendanceSession.SessionStatus status, LocalDateTime now);
    List<AttendanceSession> findByCourseCodeAndStatusAndExpiresAtAfter(String courseCode, AttendanceSession.SessionStatus status, LocalDateTime now);
    List<AttendanceSession> findAllByOrderByCreatedAtDesc();
}
