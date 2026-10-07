package com.genius.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalEmailService extends EmailService {
    @Override
    public void sendVerification(String toEmail, String token) {
        System.out.println("LOCAL VERIFICATION " + toEmail + " " + token);
    }
}
