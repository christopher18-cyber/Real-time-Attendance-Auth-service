package com.genius.service;

import com.genius.dto.SessionView;
import com.genius.model.AttendanceSession;
import com.genius.model.Course;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.AttendanceRepo;
import com.genius.repo.AttendanceSessionRepository;
import com.genius.repo.CourseRepository;
import com.genius.repo.CourseRosterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock AttendanceRepo attendanceRepo;
    @Mock AttendanceSessionRepository sessionRepo;
    @Mock CourseRepository courseRepo;
    @Mock CourseRosterRepository rosterRepo;
    @Mock CourseService courseService;
    @InjectMocks AttendanceService service;

    private User student(String email, String matricNo) {
        User user = new User();
        user.setId(12L);
        user.setEmail(email);
        user.setMatricNo(matricNo);
        user.setRole(Role.STUDENT);
        user.setEmailVerified(true);
        return user;
    }

    private AttendanceSession session() {
        AttendanceSession session = new AttendanceSession();
        session.setId(4L);
        session.setCourseCode("CSC 301");
        session.setSessionCode("ABC123");
        session.setStatus(AttendanceSession.SessionStatus.ACTIVE);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(4));
        session.setLatitude(7.37);
        session.setLongitude(3.94);
        session.setRosterSnapshotJson("[{\"name\":\"Ada Test\",\"matricNo\":\"CSC/001\",\"email\":\"ada@student.oauife.edu.ng\"}]");
        return session;
    }

    @Test
    void studentSessionHidesCodeCoordinatesAndRoster() {
        AttendanceSession session = session();
        Course course = new Course();
        course.setId(8L);
        User student = student("ada@student.oauife.edu.ng", "CSC/001");
        when(sessionRepo.findById(4L)).thenReturn(Optional.of(session));
        when(courseRepo.findByCourseCode("CSC 301")).thenReturn(Optional.of(course));
        when(attendanceRepo.findBySessionId(4L)).thenReturn(List.of());

        SessionView view = service.getSession(4L, student);
        assertNull(view.sessionCode());
        assertNull(view.latitude());
        assertNull(view.longitude());
        assertTrue(view.rosterSnapshot().isEmpty());
        assertEquals("ABSENT", view.myStatus());
    }

    @Test
    void unrelatedStudentCannotViewSession() {
        AttendanceSession session = session();
        Course course = new Course();
        when(sessionRepo.findById(4L)).thenReturn(Optional.of(session));
        when(courseRepo.findByCourseCode("CSC 301")).thenReturn(Optional.of(course));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getSession(4L, student("other@student.oauife.edu.ng", "CSC/002")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void geofenceRejectsDistantCheckInBeforeFaceComparison() {
        AttendanceSession session = session();
        User student = student("ada@student.oauife.edu.ng", "CSC/001");
        when(sessionRepo.findById(4L)).thenReturn(Optional.of(session));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.checkIn(4L, student, "ABC123", 8.0, 4.0, "[]"));
        assertTrue(error.getMessage().contains("outside"));
        verify(attendanceRepo, never()).save(any());
    }

    @Test
    void reportRequiresCourseManagerAndEndedSession() {
        AttendanceSession session = session();
        Course course = new Course();
        User lecturer = new User();
        lecturer.setRole(Role.LECTURER);
        when(sessionRepo.findById(4L)).thenReturn(Optional.of(session));
        when(courseRepo.findByCourseCode("CSC 301")).thenReturn(Optional.of(course));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(courseService).requireManager(course, lecturer);
        assertThrows(ResponseStatusException.class, () -> service.getReportSession(4L, lecturer));

        reset(courseService);
        assertThrows(IllegalStateException.class, () -> service.getReportSession(4L, lecturer));
    }
}
