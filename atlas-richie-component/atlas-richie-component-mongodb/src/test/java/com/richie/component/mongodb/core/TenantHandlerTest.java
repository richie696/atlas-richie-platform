package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.TenantScoped;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void fillOnInsert_whenTenantScopedAndContextSet() {
        TenantContext.set("tenant-abc");
        TenantScopedEntity entity = new TenantScopedEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.getTenantId()).isEqualTo("tenant-abc");
    }

    @Test
    void fillOnInsert_whenTenantScopedAndContextNotSet() {
        TenantScopedEntity entity = new TenantScopedEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    void fillOnInsert_whenNotTenantScoped_shouldNotThrow() {
        PlainEntity entity = new PlainEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.getName()).isNull();
    }

    @Test
    void getTenantField_whenCached_shouldReturnFromCache() {
        String first = handler.getTenantField(TenantScopedEntity.class);
        String second = handler.getTenantField(TenantScopedEntity.class);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void getTenantField_whenInSuperclass_shouldFindInSuperclass() {
        String fieldName = handler.getTenantField(ChildTenantScopedEntity.class);
        assertThat(fieldName).isEqualTo("tenantId");
    }

    @TenantScoped("tenantId")
    private static class TenantScopedEntity {
        private String tenantId;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }

    private static class PlainEntity {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @TenantScoped("tenantId")
    private static class ChildTenantScopedEntity extends TenantScopedEntity {
    }
}