package com.genius.service;

import com.genius.config.EmailService;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @InjectMocks AuthService service;

    @Test
    void unverifiedAccountCannotLoginWithCorrectPassword() {
        User user = new User();
        user.setEmail("ada@student.oauife.edu.ng");
        user.setPassword("hashed");
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.authenticateUser(user.getEmail(), "secret"));
        assertTrue(error.getMessage().contains("Verify your email"));
    }

    @Test
    void faceEnrollmentRequiresVerifiedStudentAndValidDescriptor() {
        User user = new User();
        user.setUsername("ada@student.oauife.edu.ng");
        user.setRole(Role.STUDENT);
        when(userRepo.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> service.saveFacialEmbedding(user.getUsername(), "[]"));
        user.setEmailVerified(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.saveFacialEmbedding(user.getUsername(), "[1,2]"));
        verify(userRepo, never()).save(user);
    }
}
