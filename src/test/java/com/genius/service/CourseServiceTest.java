package com.genius.service;

import com.genius.model.Course;
import com.genius.model.CourseRoster;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.CourseRepository;
import com.genius.repo.CourseRosterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock CourseRepository courseRepo;
    @Mock CourseRosterRepository rosterRepo;
    @InjectMocks CourseService service;

    @Test
    void studentCourseAssignmentRequiresBothEmailAndMatricNumber() {
        User student = new User();
        student.setEmail("ada@student.oauife.edu.ng");
        student.setMatricNo("CSC/001");
        student.setRole(Role.STUDENT);
        student.setEmailVerified(true);
        CourseRoster row = new CourseRoster();
        row.setEmail("other@student.oauife.edu.ng");
        row.setMatricNo("CSC/001");
        row.setCourseCode("CSC 301");
        when(rosterRepo.findByMatricNoAndConfirmed("CSC/001", true)).thenReturn(List.of(row));

        assertTrue(service.getStudentEnrolledCourses(student).isEmpty());
        verifyNoInteractions(courseRepo);

        row.setEmail(student.getEmail());
        Course course = new Course();
        course.setStatus(Course.CourseStatus.ACTIVE);
        when(courseRepo.findByCourseCode("CSC 301")).thenReturn(Optional.of(course));
        assertEquals(List.of(course), service.getStudentEnrolledCourses(student));
    }

    @Test
    void unrelatedStudentCannotReadCourseRoster() {
        User student = new User();
        student.setEmail("ada@student.oauife.edu.ng");
        student.setMatricNo("CSC/001");
        student.setRole(Role.STUDENT);
        student.setEmailVerified(true);
        Course course = new Course();
        course.setId(5L);
        course.setLecturerId(9L);
        when(courseRepo.findById(5L)).thenReturn(Optional.of(course));
        when(rosterRepo.findByMatricNoAndConfirmed("CSC/001", true)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getCourseDetail(5L, student));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
}
