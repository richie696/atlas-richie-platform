package com.richie.component.mongodb.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGet() {
        TenantContext.set("tenant-123");
        assertThat(TenantContext.get()).isEqualTo("tenant-123");
    }

    @Test
    void get_returnsNullWhenNotSet() {
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void clear() {
        TenantContext.set("tenant-123");
        TenantContext.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void require_throwsWhenNotSet() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TenantContext has not been set");
    }

    @Test
    void require_returnsValueWhenSet() {
        TenantContext.set("tenant-456");
        assertThat(TenantContext.require()).isEqualTo("tenant-456");
    }
}