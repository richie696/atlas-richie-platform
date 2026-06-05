package com.richie.component.cache.redis.migration;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationWindowValidatorTest {

    @Test
    void runValidation_whenNoViolations_passes() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatCode(() -> validator.runValidation(() -> LocalDate.of(2026, 11, 30)))
                .doesNotThrowAnyException();
    }

    @Test
    void runValidation_whenExpiredViolations_throws() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.getPerf().setEnabled(false);
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatThrownBy(() -> validator.runValidation(() -> LocalDate.of(2026, 12, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Migration windows expired");
    }

    @Test
    void runValidation_whenPropertiesNull_isNoOp() {
        MigrationWindowValidator validator = new MigrationWindowValidator(null);

        assertThatCode(() -> validator.runValidation(LocalDate::now)).doesNotThrowAnyException();
    }
}
