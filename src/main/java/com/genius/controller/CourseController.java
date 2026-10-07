package com.genius.controller;

import com.genius.model.Course;
import com.genius.dto.CourseView;
import com.genius.model.User;
import com.genius.service.AuthService;
import com.genius.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private AuthService authService;

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody Map<String, Object> payload, Principal principal) {
        try {
            String courseCode = (String) payload.get("courseCode");
            String title = (String) payload.get("title");
            String semester = (String) payload.get("semester");
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            List<?> requestedEmails = payload.get("supportingLecturerEmails") instanceof List<?> emails ? emails : List.of();
            List<String> supportingEmails = requestedEmails.stream().map(String::valueOf).toList();
            Course course = courseService.createCourse(courseCode, title, semester,
                    (String) payload.get("room"), (String) payload.get("schedule"), supportingEmails, lecturer);
            return ResponseEntity.ok(CourseView.from(course, List.of(), true));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMyCourses(Principal principal) {
        try {
            User user = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(courseService.getMyCourses(user));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourse(@PathVariable Long courseId, Principal principal) {
        try {
            User user = authService.getUserByEmailOrUsername(principal.getName());
            return ResponseEntity.ok(courseService.getCourseDetail(courseId, user));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{courseCode}/roster-upload")
    public ResponseEntity<?> uploadRoster(
            @PathVariable String courseCode,
            @RequestParam("file") MultipartFile file, Principal principal) {
        try {
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            courseService.uploadRosterCsv(courseCode, file, lecturer);
            return ResponseEntity.ok(Map.of("success", true, "message", "Roster staged successfully from CSV."));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{courseCode}/confirm-roster")
    public ResponseEntity<?> confirmRoster(@PathVariable String courseCode, Principal principal) {
        try {
            User lecturer = authService.getUserByEmailOrUsername(principal.getName());
            Course course = courseService.confirmRoster(courseCode, lecturer);
            return ResponseEntity.ok(courseService.getCourseDetail(course.getId(), lecturer));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
