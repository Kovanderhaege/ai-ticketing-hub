package com.kova.aiticketinghub.ticket.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AppUser {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private UserRole role = UserRole.USER;

    public UserRole getRole() { return role == null ? UserRole.USER : role; }
    public void grantAdmin() { role = UserRole.ADMIN; }

    public AppUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
