/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.TenantScoped;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantHandlerTest {

    private static ThreadLocalHolder holder;
    private TenantHandler handler;

    @BeforeAll
    static void initContext() {
        holder = new ThreadLocalHolder();
        TenantContext.init(holder);
    }

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
        holder.set(new TenantPrincipal().setTenantId(123L));
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
        holder.set(new TenantPrincipal().setTenantId(123L));
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
        holder.set(new TenantPrincipal().setTenantId(123L));
        TenantScopedEntity entity = new TenantScopedEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.getTenantId()).isEqualTo("123");
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
