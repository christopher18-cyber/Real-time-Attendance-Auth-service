package com.genius.service;

import com.genius.config.EmailService;
import com.genius.dto.RegisterRequest;
import com.genius.model.Role;
import com.genius.model.User;
import com.genius.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;


@Service
@Transactional
public class AuthService {

    @Autowired
    private UserRepository userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public User registerUser(RegisterRequest request){
        if(userRepo.existsByEmail(request.getEmail())){
            throw new RuntimeException("Error: Email already in use.");
        }
        if(userRepo.existsByUsername(request.getUsername())){
            throw new RuntimeException("Error: Username already in use.");
        }

        // Student Validation Logic
        if(request.getRole() == Role.STUDENT){
            if(!request.getEmail().endsWith("@student.oauife.edu.ng")){
                throw new IllegalArgumentException("Student email must end with @student.oauife.edu.ng");
            }
            if(request.getMatricNo() == null || request.getMatricNo().trim().isEmpty()) {
                throw new IllegalArgumentException("Matriculation number is required for students");
            }
            if(userRepo.findByMatricNo(request.getMatricNo()).isPresent()){
                throw new RuntimeException("Error: Matric number already registered!");
            }
        }

        else if(request.getRole() == Role.LECTURER){
            if(request.getEmail().endsWith("@student.oauife.edu.ng")){
                throw new IllegalArgumentException("Lecturer email cannot use the student domain.");
            }
            // Lecturers do not use matric numbers, so ensure it's null or clear
            request.setMatricNo(null);
        } else {
            throw new IllegalArgumentException("Invalid user role specified.");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setMatricNo(request.getMatricNo());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setFacialEmbedding(null);

        user.setEmailVerified(false);
        String verificationToken = String.format("%06d", new java.security.SecureRandom().nextInt(900000) + 100000);
        user.setEmailVerificationToken(verificationToken);

        User savedUser = userRepo.save(user);

        emailService.sendVerification(savedUser.getEmail(), verificationToken);

        return savedUser;
    }

    public User authenticateUser(String emailOrUsername, String password) {
        User user = userRepo.findByEmail(emailOrUsername)
                .or(()->userRepo.findByUsername(emailOrUsername))
                .orElseThrow(()-> new RuntimeException("Error: Invalid email/username or password"));


        if(!passwordEncoder.matches(password,user.getPassword())){
            throw new RuntimeException("Error: Invalid username/email or password");
        }

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Verify your email before signing in.");
        }

        return user;
    }


    public void resendVerificationToken(String email){
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with this email does not exist"));

        if(user.isEmailVerified()){
            throw new RuntimeException("This email is already verified. Please log in.");
        }

        // Generate a new 6-digit code
        String newCode = String.format("%06d", new java.security.SecureRandom().nextInt(900000) + 100000);
        user.setEmailVerificationToken(newCode);
        userRepo.save(user);

        // Send the new 6-digit code via email
        emailService.sendVerification(user.getEmail(), newCode);
    }

    public Role verifyUserEmail(String email, String token) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email is already verified.");
        }

        if (user.getEmailVerificationToken() == null || !user.getEmailVerificationToken().equals(token)) {
            throw new RuntimeException("Invalid verification code.");
        }

        // Mark as verified & clear token
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepo.save(user);

        return user.getRole();
    }

    public void saveFacialEmbedding(String username, String facialEmbeddingJson) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() != Role.STUDENT) {
            throw new RuntimeException("Facial onboarding is only available for students.");
        }

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Verify your email before facial onboarding.");
        }

        try {
            double[] descriptor = objectMapper.readValue(facialEmbeddingJson, double[].class);
            if (descriptor.length != 128) throw new IllegalArgumentException();
            double norm = 0;
            for (double value : descriptor) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException();
                norm += value * value;
            }
            if (norm == 0 || !Double.isFinite(norm)) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("A valid 128-value face descriptor is required.");
        }

        user.setFacialEmbedding(facialEmbeddingJson);
        userRepo.save(user);
    }

    public User getUserByEmailOrUsername(String identifier) {
        return userRepo.findByEmail(identifier)
                .orElseGet(() -> userRepo.findByUsername(identifier)
                        .orElseThrow(() -> new RuntimeException("User not found with email or username: " + identifier)));
    }
}
