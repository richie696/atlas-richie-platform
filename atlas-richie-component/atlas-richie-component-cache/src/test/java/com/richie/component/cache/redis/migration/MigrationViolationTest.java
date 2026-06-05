package com.richie.component.cache.redis.migration;

import com.richie.context.migration.MigrationWindow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationViolationTest {

    @Test
    void describe_containsKeyFields() throws Exception {
        Field field = SampleFlags.class.getDeclaredField("enabled");
        MigrationWindow window = field.getAnnotation(MigrationWindow.class);
        MigrationViolation violation = new MigrationViolation(
                SampleFlags.class,
                field,
                window,
                false,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2));

        assertThat(violation.describe())
                .contains("SampleFlags.enabled")
                .contains("until=2026-12-01")
                .contains("now=2026-12-02");
    }

    @Test
    void validateShape_rejectsNonBooleanField() throws Exception {
        Field field = SampleFlags.class.getDeclaredField("notFlag");
        MigrationWindow window = field.getAnnotation(MigrationWindow.class);
        MigrationViolation violation = new MigrationViolation(
                SampleFlags.class,
                field,
                window,
                false,
                LocalDate.now(),
                LocalDate.now());

        assertThatThrownBy(violation::validateShape)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boolean fields");
    }

    static class SampleFlags {
        @MigrationWindow(until = "2026-12-01", removedIn = "1.0.0", owner = "t", reason = "r")
        boolean enabled;

        @MigrationWindow(until = "2026-12-01", removedIn = "1.0.0", owner = "t", reason = "r")
        String notFlag;
    }
}
