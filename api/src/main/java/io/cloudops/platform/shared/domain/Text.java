package io.cloudops.platform.shared.domain;

import java.util.Objects;

/**
 * Normalization applied to free text before it is stored, so that surrounding whitespace never
 * reaches the database and a blank optional value is stored as {@code NULL} rather than as an
 * empty string that the UI would render as present.
 */
public final class Text {

    private Text() {
    }

    /**
     * @throws NullPointerException if {@code value} is null
     */
    public static String required(String value) {
        return Objects.requireNonNull(value).strip();
    }

    /**
     * @return the stripped value, or {@code null} if it is null or blank
     */
    public static String optional(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
