package com.richie.component.mqtt.filter.datasource.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.KeyAlreadyExistsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryDatasourceHandlerImplTest {

    private MemoryDatasourceHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new MemoryDatasourceHandlerImpl();
    }

    @Test
    void saveCache_thenIsDuplicate() {
        String hash = "hash-" + System.nanoTime();
        assertThat(handler.isDuplicate(hash)).isFalse();

        handler.saveCache(hash, 60_000L);
        assertThat(handler.isDuplicate(hash)).isTrue();
    }

    @Test
    void saveCache_duplicateThrows() {
        String hash = "dup-" + System.nanoTime();
        handler.saveCache(hash, 60_000L);

        assertThatThrownBy(() -> handler.saveCache(hash, 60_000L))
                .isInstanceOf(KeyAlreadyExistsException.class);
    }

    @Test
    void blankHash_treatedAsDuplicate() {
        assertThat(handler.isDuplicate(null)).isTrue();
        assertThat(handler.isDuplicate("")).isTrue();
    }

    @Test
    void saveCache_blankHash_isNoOp() {
        handler.saveCache(null, 60_000L);
        handler.saveCache("", 60_000L);
        assertThat(handler.isDuplicate("any")).isFalse();
    }

    @Test
    void clearTimeout_removesExpiredEntries() throws Exception {
        String hash = "exp-" + System.nanoTime();
        handler.saveCache(hash, -1L);
        assertThat(handler.isDuplicate(hash)).isTrue();

        var method = MemoryDatasourceHandlerImpl.class.getDeclaredMethod("clearTimeout");
        method.setAccessible(true);
        method.invoke(handler);

        assertThat(handler.isDuplicate(hash)).isFalse();
    }
}
