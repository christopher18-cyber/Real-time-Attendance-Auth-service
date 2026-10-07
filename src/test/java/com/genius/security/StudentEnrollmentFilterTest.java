package com.genius.security;

import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentEnrollmentFilterTest {
    @Mock UserRepository userRepository;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private MockHttpServletResponse request(String path, AtomicBoolean continued) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new StudentEnrollmentFilter(userRepository).doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> continued.set(true));
        return response;
    }

    @Test
    void unenrolledStudentCannotAccessWorkspaceButCanFinishOnboarding() throws Exception {
        User student = new User();
        student.setRole(Role.STUDENT);
        student.setEmailVerified(true);
        authenticate("student@student.oauife.edu.ng");
        when(userRepository.findByUsername("student@student.oauife.edu.ng")).thenReturn(Optional.of(student));

        for (String path : List.of("/api/courses/mine", "/api/attendance/sessions/active",
                "/api/attendance/sessions/history", "/api/reports/sessions/1/pdf")) {
            AtomicBoolean continued = new AtomicBoolean();
            MockHttpServletResponse response = request(path, continued);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("FACE_ENROLLMENT_REQUIRED"));
            assertFalse(continued.get());
        }

        for (String path : List.of("/api/auth/me", "/api/auth/onboard-face")) {
            AtomicBoolean continued = new AtomicBoolean();
            assertEquals(200, request(path, continued).getStatus());
            assertTrue(continued.get());
        }
    }

    @Test
    void enrolledStudentAndLecturerCanContinue() throws Exception {
        User student = new User();
        student.setRole(Role.STUDENT);
        student.setEmailVerified(true);
        student.setFacialEmbedding("[1]");
        authenticate("student@student.oauife.edu.ng");
        when(userRepository.findByUsername("student@student.oauife.edu.ng")).thenReturn(Optional.of(student));
        AtomicBoolean continued = new AtomicBoolean();
        assertEquals(200, request("/api/courses/mine", continued).getStatus());
        assertTrue(continued.get());

        User lecturer = new User();
        lecturer.setRole(Role.LECTURER);
        lecturer.setEmailVerified(true);
        authenticate("lecturer@oauife.edu.ng");
        when(userRepository.findByUsername("lecturer@oauife.edu.ng")).thenReturn(Optional.of(lecturer));
        continued.set(false);
        assertEquals(200, request("/api/courses/mine", continued).getStatus());
        assertTrue(continued.get());
    }
}
