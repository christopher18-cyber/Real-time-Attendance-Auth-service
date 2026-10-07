package com.genius.dto;

import java.util.List;

public record SessionView(
        Long id, Long courseId, String courseCode, String createdAt, String expiresAt,
        String status, String sessionCode, Double latitude, Double longitude,
        int rosterCount, int presentCount, List<CheckInView> checkIns,
        List<CourseView.RosterEntry> rosterSnapshot, String myStatus, String myCheckedInAt) {

    public record CheckInView(String userId, String name, String email, String matricNo, String at) {}
}
