package com.richie.component.dao.snowflake;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdBuilderTest {

    @Test
    void nextId_generatesMonotonicValuesForFixedWorker() {
        IdBuilder builder = new IdBuilder(42L);
        long first = builder.nextId();
        long second = builder.nextId();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void nextId_generatesUniqueValuesInBurst() {
        IdBuilder builder = new IdBuilder(7L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(builder.nextId());
        }
        assertThat(ids).hasSize(100);
    }

    @Test
    void constructor_rejectsWorkerIdAboveMax() {
        assertThatThrownBy(() -> new IdBuilder(1024L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker Id");
    }

    @Test
    void constructor_rejectsNegativeWorkerId() {
        assertThatThrownBy(() -> new IdBuilder(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsBoundaryWorkerIds() {
        assertThatCode(() -> new IdBuilder(0L)).doesNotThrowAnyException();
        assertThatCode(() -> new IdBuilder(1023L)).doesNotThrowAnyException();
    }

    @Test
    void defaultConstructor_generatesIds() {
        IdBuilder builder = new IdBuilder();
        assertThat(builder.nextId()).isPositive();
    }

    @Test
    void nextId_staysUniqueUnderRapidCalls() {
        IdBuilder builder = new IdBuilder(0L);
        long previous = builder.nextId();
        for (int i = 0; i < 200; i++) {
            long current = builder.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }
}
