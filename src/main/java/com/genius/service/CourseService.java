package com.genius.service;

import com.genius.model.Course;
import com.genius.model.CourseAccessRequest;
import com.genius.model.CourseRoster;
import com.genius.model.User;
import com.genius.model.Role;
import com.genius.dto.CourseView;
import com.genius.repo.CourseAccessRequestRepository;
import com.genius.repo.CourseRepository;
import com.genius.repo.CourseRosterRepository;
import com.genius.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

@Service
@Transactional
public class CourseService {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private CourseRosterRepository rosterRepo;

    @Autowired
    private CourseAccessRequestRepository accessRequestRepo;

    @Autowired
    private UserRepository userRepo;

    // 1. Lecturer creates course in DRAFT state
    public Course createCourse(String courseCode, String title, String semester, String room, String schedule,
                               List<String> supportingEmails, User lecturer) {
        requireLecturer(lecturer);
        if (courseCode == null || courseCode.isBlank() || title == null || title.isBlank() || semester == null || semester.isBlank()) {
            throw new IllegalArgumentException("Course code, title and semester are required.");
        }
        String upperCode = courseCode.trim().toUpperCase(Locale.ROOT);
        if (courseRepo.findByCourseCode(upperCode).isPresent()) {
            throw new RuntimeException("Course code already exists!");
        }

        Course course = new Course();
        course.setCourseCode(upperCode);
        course.setTitle(title.trim());
        course.setSemester(semester.trim());
        course.setLecturerId(lecturer.getId());
        course.setRoom(room == null ? null : room.trim());
        course.setSchedule(schedule == null ? null : schedule.trim());
        Set<String> emails = new HashSet<>();
        if (supportingEmails != null) for (String email : supportingEmails) {
            if (email == null || !email.trim().matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("A supporting lecturer email is invalid.");
            }
            emails.add(email.trim().toLowerCase(Locale.ROOT));
        }
        course.setSupportingLecturerEmails(emails);
        course.setStatus(Course.CourseStatus.DRAFT);

        return courseRepo.save(course);
    }

    // 2. Upload CSV Roster (Populates staging table without making fake accounts)
    public void uploadRosterCsv(String courseCode, MultipartFile file, User lecturer) {
        String upperCode = courseCode.toUpperCase();
        Course course = courseRepo.findByCourseCode(upperCode)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        requireManager(course, lecturer);

        if (course.getStatus() == Course.CourseStatus.ACTIVE) {
            throw new RuntimeException("Cannot modify roster for an active course. Switch back to draft or manage manually.");
        }

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            Iterable<CSVRecord> csvRecords = csvParser.getRecords();
            List<CourseRoster> rosterList = new ArrayList<>();
            Set<String> matricNumbers = new HashSet<>();
            Set<String> emails = new HashSet<>();

            for (CSVRecord csvRecord : csvRecords) {
                String fullName = csvRecord.get("name").trim();
                String matricNo = csvRecord.get("matricNo").trim();
                String email = csvRecord.get("email").trim().toLowerCase(Locale.ROOT);
                if (fullName.isBlank() || matricNo.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Roster row " + csvRecord.getRecordNumber() + " is incomplete or invalid.");
                }
                if (!matricNumbers.add(matricNo.toLowerCase(Locale.ROOT)) || !emails.add(email)) {
                    throw new IllegalArgumentException("Roster contains duplicate matric numbers or emails.");
                }

                CourseRoster roster = new CourseRoster();
                roster.setCourseCode(upperCode);
                roster.setFullName(fullName);
                roster.setMatricNo(matricNo);
                roster.setEmail(email);
                roster.setConfirmed(false);
                rosterList.add(roster);
            }
            if (rosterList.isEmpty()) throw new IllegalArgumentException("The roster CSV has no students.");
            rosterRepo.deleteAll(rosterRepo.findByCourseCodeAndConfirmed(upperCode, false));
            rosterRepo.flush();
            rosterRepo.saveAll(rosterList);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }
    }

    // 3. Lecturer confirms roster and flips course to ACTIVE
    public Course confirmRoster(String courseCode, User lecturer) {
        String upperCode = courseCode.toUpperCase();
        Course course = courseRepo.findByCourseCode(upperCode)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        requireManager(course, lecturer);

        List<CourseRoster> stagingRows = rosterRepo.findByCourseCodeAndConfirmed(upperCode, false);
        if (stagingRows.isEmpty() && rosterRepo.findByCourseCodeAndConfirmed(upperCode, true).isEmpty()) {
            throw new IllegalArgumentException("Upload at least one roster row before confirming.");
        }
        for (CourseRoster row : stagingRows) {
            row.setConfirmed(true);
        }
        rosterRepo.saveAll(stagingRows);

        course.setStatus(Course.CourseStatus.ACTIVE);
        return courseRepo.save(course);
    }

    // 4. Student Auto-Discovery (Fetches active courses matching student's matric number)
    public List<Course> getStudentEnrolledCourses(User student) {
        if (student.getRole() != Role.STUDENT || !student.isEmailVerified() || student.getMatricNo() == null || student.getMatricNo().isBlank()) {
            return new ArrayList<>();
        }

        // Find confirmed roster entries matching this student's matric number
        List<CourseRoster> rosters = rosterRepo.findByMatricNoAndConfirmed(student.getMatricNo(), true);
        List<Course> activeCourses = new ArrayList<>();

        for (CourseRoster roster : rosters) {
            if (!roster.getEmail().equalsIgnoreCase(student.getEmail())) continue;
            courseRepo.findByCourseCode(roster.getCourseCode())
                    .filter(course -> course.getStatus() == Course.CourseStatus.ACTIVE)
                    .ifPresent(activeCourses::add);
        }

        return activeCourses;
    }

    public List<CourseView> getMyCourses(User user) {
        if (!user.isEmailVerified()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email first.");
        List<Course> courses;
        if (user.getRole() == Role.LECTURER) {
            LinkedHashMap<Long, Course> managed = new LinkedHashMap<>();
            courseRepo.findByLecturerId(user.getId()).forEach(course -> managed.put(course.getId(), course));
            courseRepo.findSupportingCourses(user.getEmail()).forEach(course -> managed.put(course.getId(), course));
            courses = new ArrayList<>(managed.values());
        } else {
            courses = getStudentEnrolledCourses(user);
        }
        return courses.stream().map(course -> CourseView.from(course,
                rosterRepo.findByCourseCodeAndConfirmed(course.getCourseCode(), true), false)).toList();
    }

    public CourseView getCourseDetail(Long courseId, User user) {
        Course course = courseRepo.findById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));
        boolean manager = canManage(course, user);
        if (!manager && (user.getRole() != Role.STUDENT || getStudentEnrolledCourses(user).stream()
                .noneMatch(item -> item.getId().equals(courseId)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is unavailable to your account.");
        }
        return CourseView.from(course, rosterRepo.findByCourseCodeAndConfirmed(course.getCourseCode(), true), manager);
    }

    public boolean canManage(Course course, User user) {
        return user != null && user.getRole() == Role.LECTURER && user.isEmailVerified() &&
                (course.getLecturerId().equals(user.getId()) || course.getSupportingLecturerEmails().stream()
                        .anyMatch(email -> email.equalsIgnoreCase(user.getEmail())));
    }

    public void requireManager(Course course, User user) {
        if (!canManage(course, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lecturer access required for this course.");
    }

    private void requireLecturer(User user) {
        if (user == null || user.getRole() != Role.LECTURER || !user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verified lecturer access required.");
        }
    }

    // 5. Fallback: Request Access if missing from CSV
    public CourseAccessRequest requestCourseAccess(String courseCode, Long studentId) {
        String upperCode = courseCode.toUpperCase();
        Course course = courseRepo.findByCourseCode(upperCode)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        User student = userRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getMatricNo() == null || student.getMatricNo().trim().isEmpty()) {
            throw new RuntimeException("Student must have a registered matriculation number to request access.");
        }

        if (rosterRepo.existsByCourseCodeAndMatricNo(upperCode, student.getMatricNo())) {
            throw new RuntimeException("You are already on the course roster!");
        }

        // Check if a request already exists
        Optional<CourseAccessRequest> existing = accessRequestRepo.findByCourseCodeAndStudentId(upperCode, studentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CourseAccessRequest request = new CourseAccessRequest();
        request.setCourseCode(upperCode);
        request.setStudentId(studentId);
        request.setMatricNo(student.getMatricNo());
        request.setStatus(CourseAccessRequest.RequestStatus.PENDING);

        return accessRequestRepo.save(request);
    }

    // 6. Lecturer Approves/Rejects Access Request
    public void handleAccessRequest(Long requestId, boolean approve) {
        CourseAccessRequest request = accessRequestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (approve) {
            User student = userRepo.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Check if already on roster just in case
            if (!rosterRepo.existsByCourseCodeAndMatricNo(request.getCourseCode(), student.getMatricNo())) {
                CourseRoster roster = new CourseRoster();
                roster.setCourseCode(request.getCourseCode());
                roster.setFullName(student.getFullName());
                roster.setMatricNo(student.getMatricNo());
                roster.setEmail(student.getEmail());
                roster.setConfirmed(true); // Automatically confirmed since lecturer approved it
                rosterRepo.save(roster);
            }

            request.setStatus(CourseAccessRequest.RequestStatus.APPROVED);
        } else {
            request.setStatus(CourseAccessRequest.RequestStatus.REJECTED);
        }
        accessRequestRepo.save(request);
    }
}
