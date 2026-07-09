/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb;

import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.core.AuditFieldHandler;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantHandler;
import com.richie.component.mongodb.exception.DuplicateKeyException;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongodbTest {

    @BeforeAll
    static void initTenantContext() {
        TenantContext.init(new ThreadLocalHolder());
    }

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private EntityIntrospector entityIntrospector;

    private Mongodb mongodb;

    @BeforeEach
    void setUp() {
        AuditFieldHandler auditFieldHandler = new AuditFieldHandler();
        TenantHandler tenantHandler = new TenantHandler();
        mongodb = new Mongodb(mongoTemplate, entityIntrospector, auditFieldHandler, tenantHandler, null, null, null);
    }

    @Test
    void query_shouldReturnQueryBuilder() {
        QueryBuilder<TestEntity> result = mongodb.query(TestEntity.class);
        assertThat(result).isNotNull();
    }

    @Test
    void update_shouldReturnUpdateBuilder() {
        UpdateBuilder<TestEntity> result = mongodb.update(TestEntity.class);
        assertThat(result).isNotNull();
    }

    @Test
    void delete_shouldReturnDeleteBuilder() {
        DeleteBuilder<TestEntity> result = mongodb.delete(TestEntity.class);
        assertThat(result).isNotNull();
    }

    @Test
    void save_shouldCallMongoTemplateSave() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.save(entity)).thenReturn(entity);
        TestEntity result = mongodb.save(entity);
        verify(mongoTemplate).save(entity);
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void insert_shouldCallMongoTemplateInsert() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.insert(entity)).thenReturn(entity);
        TestEntity result = mongodb.insert(entity);
        verify(mongoTemplate).insert(entity);
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void insert_whenDuplicateKey_shouldThrowDuplicateKeyException() {
        TestEntity entity = new TestEntity();
        org.springframework.dao.DuplicateKeyException springEx = new org.springframework.dao.DuplicateKeyException("duplicate key", null);
        when(mongoTemplate.insert(entity)).thenThrow(springEx);
        assertThatThrownBy(() -> mongodb.insert(entity))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertAll_shouldCallMongoTemplateInsertWithClass() {
        List<TestEntity> entities = List.of(new TestEntity(), new TestEntity());
        when(mongoTemplate.insert(entities, TestEntity.class)).thenReturn(entities);
        List<TestEntity> result = mongodb.insertAll(entities);
        verify(mongoTemplate).insert(entities, TestEntity.class);
        assertThat(result).hasSize(2);
    }

    @Test
    void findById_shouldReturnOptionalOfEntity() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.findById("123", TestEntity.class)).thenReturn(entity);
        Optional<TestEntity> result = mongodb.findById(TestEntity.class, "123");
        assertThat(result).isPresent();
    }

    @Test
    void findById_whenNotFound_shouldReturnEmptyOptional() {
        when(mongoTemplate.findById("123", TestEntity.class)).thenReturn(null);
        Optional<TestEntity> result = mongodb.findById(TestEntity.class, "123");
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdOrThrow_whenFound_shouldReturnEntity() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.findById("123", TestEntity.class)).thenReturn(entity);
        TestEntity result = mongodb.findByIdOrThrow(TestEntity.class, "123", IllegalStateException::new);
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void findByIdOrThrow_whenNotFound_shouldThrow() {
        when(mongoTemplate.findById("123", TestEntity.class)).thenReturn(null);
        assertThatThrownBy(() -> mongodb.findByIdOrThrow(TestEntity.class, "123", IllegalStateException::new))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void existsById_shouldReturnBoolean() {
        when(mongoTemplate.exists(any(Query.class), eq(TestEntity.class))).thenReturn(true);
        boolean result = mongodb.existsById(TestEntity.class, "123");
        assertThat(result).isTrue();
    }

    @Test
    void deleteById_shouldCallRemove() {
        mongodb.deleteById(TestEntity.class, "123");
        verify(mongoTemplate).remove(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void dropCollection_shouldCallDropCollection() {
        mongodb.dropCollection(TestEntity.class);
        verify(mongoTemplate).dropCollection(TestEntity.class);
    }

    static class TestEntity {
        private String id;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
