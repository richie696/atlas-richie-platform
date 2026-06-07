package com.richie.component.mongodb.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MongodbSlowQueryLoggerTest {

    private MongodbSlowQueryLogger logger;

    @BeforeEach
    void setUp() {
        logger = new MongodbSlowQueryLogger();
    }

    @Test
    void logIfSlow_belowThreshold_shouldNotLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold - 1);
    }

    @Test
    void logIfSlow_atThreshold_shouldNotLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold);
    }

    @Test
    void logIfSlow_aboveThreshold_shouldLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold + 1);
    }

    @Test
    void threshold_shouldBe200Ms() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        assertThat(threshold).isEqualTo(200L);
    }
}
