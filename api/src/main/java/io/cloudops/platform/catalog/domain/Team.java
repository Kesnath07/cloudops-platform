package io.cloudops.platform.catalog.domain;

import io.cloudops.platform.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "teams")
public class Team extends VersionedEntity {

    @Column(nullable = false, unique = true, length = 63, updatable = false)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    protected Team() {
    }

    public Team(String slug, String name, String description, String contactEmail) {
        this.slug = Objects.requireNonNull(slug);
        rename(name, description, contactEmail);
    }

    public final void rename(String newName, String newDescription, String newContactEmail) {
        this.name = Objects.requireNonNull(newName).strip();
        this.description = newDescription;
        this.contactEmail = newContactEmail;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }
}
