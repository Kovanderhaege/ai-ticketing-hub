package com.kova.aiticketinghub.ticket.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthController(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe trop long (72 octets maximum).");
        }
        try {
            AppUser user = users.saveAndFlush(new AppUser(normalizeEmail(request.email()), passwords.encode(request.password())));
            return new UserResponse(user.getId(), user.getEmail());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        AppUser user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserResponse(user.getId(), user.getEmail());
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    // Avoid the default validation logger including rejected password values.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidRegistration() {
        return ResponseEntity.badRequest().body(Map.of("message", "Adresse e-mail ou mot de passe invalide."));
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) {}
    public record UserResponse(UUID id, String email) {}
    public record CsrfResponse(String headerName, String token) {}
}
