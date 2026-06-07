package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.TenantScoped;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import static org.assertj.core.api.Assertions.assertThat;

class TenantHandlerTest {

    private TenantHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TenantHandler();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void addTenantCriteria_whenTenantScopedAndContextSet() {
        TenantContext.set("tenant-123");
        Query query = new Query();
        handler.addTenantCriteria(query, TenantScopedEntity.class);
        assertThat(query.getQueryObject()).containsKey("tenantId");
    }

    @Test
    void addTenantCriteria_whenTenantScopedAndContextNotSet() {
        Query query = new Query();
        handler.addTenantCriteria(query, TenantScopedEntity.class);
        assertThat(query.getQueryObject()).isEmpty();
    }

    @Test
    void addTenantCriteria_whenNotTenantScoped() {
        TenantContext.set("tenant-123");
        Query query = new Query();
        handler.addTenantCriteria(query, PlainEntity.class);
        assertThat(query.getQueryObject()).isEmpty();
    }

    @Test
    void bypass_returnsFalse() {
        assertThat(handler.bypass()).isFalse();
    }

    @Test
    void getTenantField_whenTenantScoped() {
        assertThat(handler.getTenantField(TenantScopedEntity.class)).isEqualTo("tenantId");
    }

    @Test
    void getTenantField_whenNotTenantScoped() {
        assertThat(handler.getTenantField(PlainEntity.class)).isNull();
    }

    @TenantScoped
    private static class TenantScopedEntity {
        private String tenantId;
    }

    private static class PlainEntity {
        private String name;
    }
}