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

    public AppUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
