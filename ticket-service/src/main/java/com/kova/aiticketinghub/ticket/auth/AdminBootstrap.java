package com.kova.aiticketinghub.ticket.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${app.security.bootstrap-admin-email:}") String email,
            @Value("${app.security.bootstrap-admin-password:}") String password) {
        this.users = users; this.passwords = passwords; this.email = email; this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) return;
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || email.length() > 254
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Invalid administrator bootstrap configuration");
        String normalized = AuthController.normalizeEmail(email);
        AppUser user = users.findByEmail(normalized).orElse(null);
        if (user == null) user = new AppUser(normalized, passwords.encode(password));
        else if (!passwords.matches(password, user.getPasswordHash()))
            throw new IllegalStateException("Existing administrator bootstrap account credentials do not match");
        user.grantAdmin();
        users.saveAndFlush(user);
    }
}
