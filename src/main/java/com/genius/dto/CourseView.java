package com.genius.dto;

import com.genius.model.Course;
import com.genius.model.CourseRoster;
import java.util.List;
import java.util.Set;

public record CourseView(
        Long id, String courseCode, String title, String semester, String room, String schedule,
        Long lecturerId, String status, int rosterCount, List<RosterEntry> roster,
        Set<String> supportingLecturerEmails) {

    public record RosterEntry(String name, String matricNo, String email) {
        public static RosterEntry from(CourseRoster row) {
            return new RosterEntry(row.getFullName(), row.getMatricNo(), row.getEmail());
        }
    }

    public static CourseView from(Course course, List<CourseRoster> rows, boolean includeRoster) {
        return new CourseView(course.getId(), course.getCourseCode(), course.getTitle(), course.getSemester(),
                course.getRoom(), course.getSchedule(), course.getLecturerId(), course.getStatus().name(),
                rows.size(), includeRoster ? rows.stream().map(RosterEntry::from).toList() : List.of(),
                includeRoster ? course.getSupportingLecturerEmails() : Set.of());
    }
}
