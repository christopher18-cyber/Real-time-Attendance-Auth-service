package com.genius.controller;

import com.genius.model.Attendance;
import com.genius.model.AttendanceSession;
import com.genius.model.User;
import com.genius.service.AttendanceService;
import com.genius.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    @Autowired private AttendanceService attendanceService;
    @Autowired private AuthService authService;

    @PostMapping("/sessions")
    public ResponseEntity<?> startSession(@RequestBody Map<String, Object> payload, Principal principal) {
        try {
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            double latitude = Double.parseDouble(String.valueOf(payload.get("latitude")));
            double longitude = Double.parseDouble(String.valueOf(payload.get("longitude")));

            AttendanceSession session;
            if (payload.containsKey("courseCode") && payload.get("courseCode") != null) {
                session = attendanceService.startSession((String) payload.get("courseCode"), lecturer, latitude, longitude);
            } else if (payload.containsKey("courseId") && payload.get("courseId") != null) {
                Long courseId = Long.parseLong(String.valueOf(payload.get("courseId")));
                session = attendanceService.startSessionById(courseId, lecturer, latitude, longitude);
            } else {
                throw new IllegalArgumentException("Course code or course ID is required.");
            }

            return ResponseEntity.ok(attendanceService.getSession(session.getId(), lecturer));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<?> activeSessions(Principal principal) {
        try {
            User user = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(attendanceService.getActiveSessions(user));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/sessions/history")
    public ResponseEntity<?> sessionHistory(Principal principal) {
        try {
            User user = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(attendanceService.getSessionHistory(user));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable Long sessionId, Principal principal) {
        try {
            User user = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(attendanceService.getSession(sessionId, user));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/sessions/{sessionId}/close")
    public ResponseEntity<?> closeSession(@PathVariable Long sessionId, Principal principal) {
        try {
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            attendanceService.closeSession(sessionId, lecturer);
            return ResponseEntity.ok(attendanceService.getSession(sessionId, lecturer));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/sessions/{sessionId}/records")
    public ResponseEntity<?> getSessionRecords(@PathVariable Long sessionId, Principal principal) {
        try {
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(attendanceService.getSessionRecords(sessionId, lecturer));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/sessions/{sessionId}/check-ins")
    public ResponseEntity<?> checkIn(@PathVariable Long sessionId, @RequestBody Map<String, Object> payload,
                                     Principal principal) {
        try {
            User student = authService.getUserByEmailOrUsername(principal.getName());
            double latitude = Double.parseDouble(String.valueOf(payload.get("latitude")));
            double longitude = Double.parseDouble(String.valueOf(payload.get("longitude")));
            Attendance attendance = attendanceService.checkIn(sessionId, student, (String) payload.get("code"),
                    latitude, longitude, (String) payload.get("facialEmbedding"));
            return ResponseEntity.ok(Map.of("sessionId", sessionId, "status", "PRESENT", "checkedInAt",
                    attendance.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toString()));
        } catch (Exception e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(Exception e) {
        HttpStatus status = e instanceof ResponseStatusException response
                ? HttpStatus.valueOf(response.getStatusCode().value())
                : e instanceof IllegalStateException ? HttpStatus.CONFLICT
                  : e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = e instanceof ResponseStatusException response ? response.getReason() : e.getMessage();
        return ResponseEntity.status(status).body(Map.of("code", status.name(),
                "message", message == null ? "Request failed." : message, "details", Map.of()));
    }
}