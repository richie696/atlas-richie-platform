package com.richie.component.cache.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedListCapacityLimitsTest {

    @Test
    void ceiling_shouldBeOneBelowListBigkeyThreshold() {
        assertEquals(4_999L, BoundedListCapacityLimits.BOUNDED_MAX_LEN_CEILING);
        assertEquals(5_000L, BoundedListCapacityLimits.LIST_BIGKEY_RECOMMENDED_MAX_ELEMENTS);
    }

    @Test
    void validateMaxLen_shouldRejectOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> BoundedListCapacityLimits.validateMaxLen(0));
        assertThrows(IllegalArgumentException.class, () -> BoundedListCapacityLimits.validateMaxLen(5_000));
        assertThrows(IllegalArgumentException.class, () -> BoundedListCapacityLimits.computeDoubledCapacity(0));
    }

    @Test
    void metaKey_shouldRejectDoubleMetaSuffix() {
        assertThrows(IllegalArgumentException.class,
                () -> BoundedListCapacityLimits.metaKey("queue:already:meta"));
    }

    @Test
    void computeDoubledCapacity_shouldCapAtCeiling() {
        assertEquals(200L, BoundedListCapacityLimits.computeDoubledCapacity(100));
        assertEquals(4_999L, BoundedListCapacityLimits.computeDoubledCapacity(4_000));
        assertEquals(4_999L, BoundedListCapacityLimits.computeDoubledCapacity(4_999));
    }

    @Test
    void canGrow_shouldReflectCeiling() {
        assertTrue(BoundedListCapacityLimits.canGrow(100));
        assertFalse(BoundedListCapacityLimits.canGrow(4_999));
    }

    @Test
    void assertMaxLenMatches_shouldFailOnMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> BoundedListCapacityLimits.assertMaxLenMatches("k", 100, 200));
    }

    @Test
    void validateBatchCount_shouldRejectZeroOrOverLimit() {
        assertThrows(IllegalArgumentException.class, () -> BoundedListCapacityLimits.validateBatchCount(0));
        assertThrows(IllegalArgumentException.class,
                () -> BoundedListCapacityLimits.validateBatchCount(BoundedListCapacityLimits.MAX_BATCH_COUNT + 1));
    }

    @Test
    void parseMetaMaxLen_shouldRejectInvalid() {
        assertThrows(IllegalStateException.class,
                () -> BoundedListCapacityLimits.parseMetaMaxLen("k", "not-a-number"));
        assertThrows(IllegalArgumentException.class,
                () -> BoundedListCapacityLimits.parseMetaMaxLen("k", "6000"));
    }

    @Test
    void parseMetaMaxLen_shouldAcceptValid() {
        assertEquals(512L, BoundedListCapacityLimits.parseMetaMaxLen("k", "512"));
    }
}
