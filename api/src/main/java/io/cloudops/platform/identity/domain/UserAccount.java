package io.cloudops.platform.identity.domain;

import io.cloudops.platform.shared.domain.Text;
import io.cloudops.platform.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends VersionedEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.displayName = Text.required(displayName);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
    }

    /**
     * Emails are compared case-insensitively; storing them normalized lets a plain unique
     * constraint enforce that.
     */
    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    public void assignRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }
}
