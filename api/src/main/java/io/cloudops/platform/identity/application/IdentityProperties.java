package io.cloudops.platform.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapAdminEmail the account registered with this address becomes an administrator.
 *                            This avoids shipping seeded credentials: the first administrator sets
 *                            their own password through the normal registration flow.
 */
@ConfigurationProperties("cloudops.identity")
public record IdentityProperties(String bootstrapAdminEmail) {

    public boolean isBootstrapAdmin(String normalizedEmail) {
        return bootstrapAdminEmail != null
                && !bootstrapAdminEmail.isBlank()
                && bootstrapAdminEmail.strip().equalsIgnoreCase(normalizedEmail);
    }
}
