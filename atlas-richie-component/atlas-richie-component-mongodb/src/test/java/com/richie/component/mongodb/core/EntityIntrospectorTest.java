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
package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.AuditFields;
import com.richie.component.mongodb.annotation.ExpireAfter;
import com.richie.component.mongodb.annotation.SoftDelete;
import com.richie.component.mongodb.annotation.TenantScoped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.lang.reflect.Field;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EntityIntrospectorTest {

    private EntityIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new EntityIntrospector();
    }

    @Test
    void getCollectionName_withDocumentAnnotation_shouldReturnCollectionName() {
        String name = introspector.getCollectionName(DocumentedEntity.class);
        assertThat(name).isEqualTo("custom_collection");
    }

    @Test
    void getCollectionName_withoutDocumentAnnotation_shouldReturnLowercaseClassName() {
        String name = introspector.getCollectionName(SimpleEntity.class);
        assertThat(name).isEqualTo("simpleentity");
    }

    @Test
    void getIdFieldName_withIdAnnotation_shouldReturnIdFieldName() {
        String name = introspector.getIdFieldName(DocumentedEntity.class);
        assertThat(name).isEqualTo("customId");
    }

    @Test
    void getIdFieldName_withoutIdAnnotation_shouldReturnId() {
        String name = introspector.getIdFieldName(SimpleEntity.class);
        assertThat(name).isEqualTo("id");
    }

    @Test
    void getIndexedFields_shouldReturnFieldsWithIndexedAnnotation() {
        List<Field> fields = introspector.getIndexedFields(DocumentedEntity.class);
        assertThat(fields).hasSize(2);
    }

    @Test
    void getSoftDeleteField_withSoftDeleteAnnotation_shouldReturnFieldName() {
        assertThat(introspector.getSoftDeleteField(SoftDeleteEntity.class)).isEqualTo("deleted");
    }

    @Test
    void getSoftDeleteField_withoutSoftDeleteAnnotation_shouldReturnNull() {
        assertThat(introspector.getSoftDeleteField(SimpleEntity.class)).isNull();
    }

    @Test
    void getTenantField_withTenantScopedAnnotation_shouldReturnFieldName() {
        assertThat(introspector.getTenantField(TenantScopedEntity.class)).isEqualTo("tenantId");
    }

    @Test
    void getTenantField_withoutTenantScopedAnnotation_shouldReturnNull() {
        assertThat(introspector.getTenantField(SimpleEntity.class)).isNull();
    }

    @Test
    void hasAuditFields_withAuditFieldsAnnotation_shouldReturnTrue() {
        assertThat(introspector.hasAuditFields(AuditedEntity.class)).isTrue();
    }

    @Test
    void hasAuditFields_withoutAuditFieldsAnnotation_shouldReturnFalse() {
        assertThat(introspector.hasAuditFields(SimpleEntity.class)).isFalse();
    }

    @Test
    void getExpireAfterFields_withExpireAfterAnnotation_shouldReturnFields() {
        List<Field> fields = introspector.getExpireAfterFields(ExpireAfterEntity.class);
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getName()).isEqualTo("resetToken");
    }

    @Test
    void getExpireAfterFields_withoutExpireAfterAnnotation_shouldReturnEmpty() {
        assertThat(introspector.getExpireAfterFields(SimpleEntity.class)).isEmpty();
    }

    @Document("custom_collection")
    static class DocumentedEntity {
        @org.springframework.data.annotation.Id
        private String customId;

        @Indexed
        private String indexedField1;

        @Indexed(unique = true)
        private String indexedField2;

        private String normalField;

        public String getCustomId() { return customId; }
        public void setCustomId(String customId) { this.customId = customId; }
        public String getIndexedField1() { return indexedField1; }
        public void setIndexedField1(String indexedField1) { this.indexedField1 = indexedField1; }
        public String getIndexedField2() { return indexedField2; }
        public void setIndexedField2(String indexedField2) { this.indexedField2 = indexedField2; }
        public String getNormalField() { return normalField; }
        public void setNormalField(String normalField) { this.normalField = normalField; }
    }

    static class SimpleEntity {
        private String id;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @SoftDelete
    private static class SoftDeleteEntity {
        private Boolean deleted;
    }

    @TenantScoped
    private static class TenantScopedEntity {
        private String tenantId;
    }

    @AuditFields
    private static class AuditedEntity {
        private java.time.Instant createdAt;
        private String createdBy;
        private java.time.Instant updatedAt;
        private String updatedBy;
    }

    private static class ExpireAfterEntity {
        @ExpireAfter(seconds = 3600)
        private String resetToken;
    }
}