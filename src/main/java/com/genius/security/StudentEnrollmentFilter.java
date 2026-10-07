package com.genius.security;

import com.genius.model.Role;
import com.genius.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public class StudentEnrollmentFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentEnrollmentFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            var user = userRepository.findByUsername(authentication.getName());
            if (user.isPresent() && user.get().getRole() == Role.STUDENT) {
                if (!user.get().isEmailVerified()) {
                    reject(response, "EMAIL_VERIFICATION_REQUIRED", "Verify your email before using SmartAttend.");
                    return;
                }
                if (user.get().getFacialEmbedding() == null || user.get().getFacialEmbedding().isBlank()) {
                    reject(response, "FACE_ENROLLMENT_REQUIRED", "Complete face setup before using SmartAttend.");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", code, "message", message, "details", Map.of())));
    }
}
