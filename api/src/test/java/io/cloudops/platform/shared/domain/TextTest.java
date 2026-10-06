package io.cloudops.platform.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TextTest {

    @Test
    void requiredTextIsStripped() {
        assertThat(Text.required("  Payments API \n")).isEqualTo("Payments API");
    }

    @Test
    void requiredTextRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Text.required(null));
    }

    @Test
    void optionalTextIsStrippedAndBlankBecomesNull() {
        assertThat(Text.optional(" runbook ")).isEqualTo("runbook");
        assertThat(Text.optional(null)).isNull();
        assertThat(Text.optional("")).isNull();
        assertThat(Text.optional(" \t\n ")).isNull();
    }
}
