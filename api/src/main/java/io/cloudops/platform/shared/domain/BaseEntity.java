package io.cloudops.platform.shared.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Root of every persistent entity. Identifiers are time-ordered UUIDs generated on persist,
 * which keeps B-tree index inserts mostly sequential while still being safe to expose in URLs.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    public UUID getId() {
        return id;
    }
}
