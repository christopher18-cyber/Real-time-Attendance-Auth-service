package com.genius.controller;

import com.genius.dto.*;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.security.JwtTokenProvider;
import com.genius.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
   private AuthService authService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registrationRequest){
        try{
            User registeredUser = authService.registerUser(registrationRequest);
            return ResponseEntity.ok( new AuthResponse(
                    "user registered successfully",
                    registeredUser.getRole().name(),
                    "/verify-email"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest){
        try{
            User authenticatedUser = authService.authenticateUser(
                    loginRequest.getEmailOrUsername(),
                    loginRequest.getPassword()
            );

            String token = jwtTokenProvider.generateToken(
                    authenticatedUser.getUsername(),
                    authenticatedUser.getRole().name()
            );

            return ResponseEntity.ok(new LoginResponse(
                    "Login Successful!",
                    token,
                    authenticatedUser.getRole(),
                    "/dashboard"
            ));
        }catch (Exception e){
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }


    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationCode(@RequestParam String email){
        try{
            authService.resendVerificationToken(email);
            return ResponseEntity.ok("A new verification has been sent to your email.");
        }catch(RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailRequest request) {
        try {
            Role role = authService.verifyUserEmail(request.getEmail(), request.getToken());

            // Response containing the next route or role
            return ResponseEntity.ok(new VerificationResponse(
                    "Email verified successfully!",
                    role,
                    role == Role.STUDENT ? "/onboard-face" : "/dashboard"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @PostMapping("/onboard-face")
    public ResponseEntity<?> onBoardFace(@RequestBody Map<String,String> payload, Principal principal){
        try{
            String facialEmbedding = payload.get("facialEmbedding");
            String username = principal.getName(); // Securely from JWT

            authService.saveFacialEmbedding(username, facialEmbedding);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Facial onboarding completed successfully!"
            ));
        }catch (Exception e){
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }


    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Gets the identifier (email/username) from the secure JWT token
            String identifier = principal.getName();
            User user = authService.getUserByEmailOrUsername(identifier);

            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "fullName", user.getFullName(),
                    "email", user.getEmail(),
                    "username", user.getUsername(),
                    "role", user.getRole(),
                    "emailVerified", user.isEmailVerified(),
                    "matricNo", user.getMatricNo() != null ? user.getMatricNo() : "",
                    "faceEnrolled", user.getFacialEmbedding() != null && !user.getFacialEmbedding().isEmpty()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }
    }
}
