package com.genius.service;

import com.genius.dto.CourseView;
import com.genius.dto.SessionView;
import com.genius.model.Attendance;
import com.genius.model.AttendanceSession;
import com.genius.model.Course;
import com.genius.model.CourseRoster;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.AttendanceRepo;
import com.genius.repo.AttendanceSessionRepository;
import com.genius.repo.CourseRepository;
import com.genius.repo.CourseRosterRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AttendanceService {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double GEOFENCE_RADIUS_METERS = 100;
    private static final int SESSION_MINUTES = 5;

    @Autowired private AttendanceRepo attendanceRepo;
    @Autowired private AttendanceSessionRepository sessionRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private CourseRosterRepository rosterRepo;
    @Autowired private CourseService courseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AttendanceSession startSession(String courseCode, User lecturer, double latitude, double longitude) {
        if (courseCode == null || courseCode.isBlank()) throw new IllegalArgumentException("Course code is required.");
        validateCoordinates(latitude, longitude);
        Course course = courseRepo.findByCourseCode(courseCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));
        courseService.requireManager(course, lecturer);
        if (course.getStatus() != Course.CourseStatus.ACTIVE) {
            throw new IllegalArgumentException("Confirm the course roster before starting attendance.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!sessionRepo.findByCourseCodeAndStatusAndExpiresAtAfter(course.getCourseCode(),
                AttendanceSession.SessionStatus.ACTIVE, now).isEmpty()) {
            throw new IllegalStateException("This course already has an active attendance session.");
        }
        List<CourseView.RosterEntry> roster = rosterRepo.findByCourseCodeAndConfirmed(course.getCourseCode(), true)
                .stream().map(CourseView.RosterEntry::from).toList();
        if (roster.isEmpty()) throw new IllegalArgumentException("This course has no confirmed students.");

        AttendanceSession session = new AttendanceSession();
        session.setCourseCode(course.getCourseCode());
        session.setSessionCode(UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        session.setLecturerId(lecturer.getId());
        session.setStatus(AttendanceSession.SessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusMinutes(SESSION_MINUTES));
        session.setLatitude(latitude);
        session.setLongitude(longitude);
        session.setRosterSnapshotJson(objectMapper.writeValueAsString(roster));
        return sessionRepo.save(session);
    }

    public AttendanceSession closeSession(Long sessionId, User lecturer) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
        courseService.requireManager(courseFor(session), lecturer);
        session.setStatus(AttendanceSession.SessionStatus.CLOSED);
        return sessionRepo.save(session);
    }

    public List<SessionView> getActiveSessions(User user) {
        return sessionRepo.findByStatusAndExpiresAtAfter(AttendanceSession.SessionStatus.ACTIVE, LocalDateTime.now())
                .stream().filter(session -> canView(session, user)).map(session -> view(session, user)).toList();
    }

    public List<SessionView> getSessionHistory(User user) {
        return sessionRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(session -> session.getStatus() == AttendanceSession.SessionStatus.CLOSED ||
                        session.getExpiresAt() != null && !LocalDateTime.now().isBefore(session.getExpiresAt()))
                .filter(session -> canView(session, user)).map(session -> view(session, user)).toList();
    }

    public SessionView getReportSession(Long sessionId, User lecturer) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
        courseService.requireManager(courseFor(session), lecturer);
        if (session.getStatus() == AttendanceSession.SessionStatus.ACTIVE &&
                LocalDateTime.now().isBefore(session.getExpiresAt())) {
            throw new IllegalStateException("End attendance before downloading the report.");
        }
        return view(session, lecturer);
    }

    public SessionView getSession(Long sessionId, User user) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
        if (!canView(session, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session access denied.");
        return view(session, user);
    }

    public List<SessionView.CheckInView> getSessionRecords(Long sessionId, User lecturer) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
        courseService.requireManager(courseFor(session), lecturer);
        return attendanceRepo.findBySessionId(sessionId).stream().map(this::checkInView).toList();
    }

    public Attendance checkIn(Long sessionId, User student, String code,
                              double latitude, double longitude, String liveEmbeddingJson) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance session not found."));
        validateSessionState(session);
        if (student.getRole() != Role.STUDENT || !student.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verified student access required.");
        }
        if (code == null || !session.getSessionCode().equalsIgnoreCase(code.trim())) {
            throw new IllegalArgumentException("Invalid attendance session code.");
        }
        if (!isInSnapshot(session, student)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not on this session's roster.");
        }
        validateCoordinates(latitude, longitude);
        if (session.getLatitude() == null || session.getLongitude() == null) {
            throw new IllegalStateException("This session has no lecture location. Ask your lecturer to start a new one.");
        }
        double distance = calculateDistance(session.getLatitude(), session.getLongitude(), latitude, longitude);
        if (distance > GEOFENCE_RADIUS_METERS) {
            throw new IllegalArgumentException("You are outside the permitted lecture hall boundary.");
        }
        if (attendanceRepo.existsBySessionIdAndMatricNo(sessionId, student.getMatricNo())) {
            throw new IllegalStateException("Attendance already marked for this session.");
        }
        if (student.getFacialEmbedding() == null || !compareEmbeddings(student.getFacialEmbedding(), liveEmbeddingJson)) {
            throw new IllegalArgumentException("Face verification failed.");
        }

        Attendance attendance = new Attendance();
        attendance.setUser(student);
        attendance.setMatricNo(student.getMatricNo());
        attendance.setCourseCode(session.getCourseCode());
        attendance.setSessionId(sessionId);
        attendance.setDate(LocalDate.now());
        attendance.setTimestamp(LocalDateTime.now());
        attendance.setStatus("PRESENT");
        return attendanceRepo.save(attendance);
    }

    public List<CourseView.RosterEntry> snapshot(AttendanceSession session) {
        if (session.getRosterSnapshotJson() == null) return List.of();
        try {
            return Arrays.asList(objectMapper.readValue(session.getRosterSnapshotJson(), CourseView.RosterEntry[].class));
        } catch (Exception e) {
            throw new IllegalStateException("Session roster snapshot is invalid.");
        }
    }

    private SessionView view(AttendanceSession session, User user) {
        Course course = courseFor(session);
        boolean lecturer = courseService.canManage(course, user);
        List<Attendance> records = attendanceRepo.findBySessionId(session.getId());
        List<CourseView.RosterEntry> roster = snapshot(session);
        String status = session.getStatus() == AttendanceSession.SessionStatus.ACTIVE &&
                LocalDateTime.now().isAfter(session.getExpiresAt()) ? "EXPIRED" : session.getStatus().name();
        Attendance mine = lecturer ? null : records.stream()
                .filter(record -> record.getUser().getId().equals(user.getId())).findFirst().orElse(null);
        return new SessionView(session.getId(), course.getId(), course.getCourseCode(),
                utc(session.getCreatedAt()), utc(session.getExpiresAt()), status,
                lecturer ? session.getSessionCode() : null,
                lecturer ? session.getLatitude() : null, lecturer ? session.getLongitude() : null,
                roster.size(), records.size(),
                lecturer ? records.stream().map(this::checkInView).toList() : List.of(),
                lecturer ? roster : List.of(),
                lecturer ? null : mine == null ? "ABSENT" : "PRESENT",
                mine == null ? null : utc(mine.getTimestamp()));
    }

    private SessionView.CheckInView checkInView(Attendance record) {
        User student = record.getUser();
        return new SessionView.CheckInView(String.valueOf(student.getId()), student.getFullName(),
                student.getEmail(), record.getMatricNo(), utc(record.getTimestamp()));
    }

    private boolean canView(AttendanceSession session, User user) {
        if (user == null || !user.isEmailVerified()) return false;
        Course course = courseFor(session);
        return courseService.canManage(course, user) || isInSnapshot(session, user);
    }

    private boolean isInSnapshot(AttendanceSession session, User user) {
        return user != null && user.getRole() == Role.STUDENT && user.isEmailVerified() &&
                user.getMatricNo() != null && snapshot(session).stream().anyMatch(row ->
                row.email().equalsIgnoreCase(user.getEmail()) && row.matricNo().equalsIgnoreCase(user.getMatricNo()));
    }

    private Course courseFor(AttendanceSession session) {
        return courseRepo.findByCourseCode(session.getCourseCode())
                .orElseThrow(() -> new IllegalStateException("The session's course no longer exists."));
    }

    private void validateSessionState(AttendanceSession session) {
        if (session.getStatus() != AttendanceSession.SessionStatus.ACTIVE ||
                LocalDateTime.now().isAfter(session.getExpiresAt())) {
            throw new IllegalStateException("This attendance session has ended.");
        }
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Valid latitude and longitude are required.");
        }
    }

    private boolean compareEmbeddings(String storedJson, String liveJson) {
        try {
            double[] stored = objectMapper.readValue(storedJson, double[].class);
            double[] live = objectMapper.readValue(liveJson, double[].class);
            if (stored.length != 128 || live.length != 128) return false;
            double dot = 0, storedNorm = 0, liveNorm = 0;
            for (int i = 0; i < 128; i++) {
                if (!Double.isFinite(stored[i]) || !Double.isFinite(live[i])) return false;
                dot += stored[i] * live[i];
                storedNorm += stored[i] * stored[i];
                liveNorm += live[i] * live[i];
            }
            return storedNorm > 0 && liveNorm > 0 &&
                    dot / Math.sqrt(storedNorm * liveNorm) >= 0.65;
        } catch (Exception e) {
            return false;
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    private String utc(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}
