/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.core;

import com.richie.component.mongodb.annotation.AuditFields;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class AuditFieldHandlerTest {

    private AuditFieldHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuditFieldHandler();
    }

    @Test
    void fillOnInsert_setsAllFourFields() {
        AuditedEntity entity = new AuditedEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.createdAt).isNotNull();
        assertThat(entity.createdBy).isEqualTo("system");
        assertThat(entity.updatedAt).isNotNull();
        assertThat(entity.updatedBy).isEqualTo("system");
    }

    @Test
    void fillOnInsert_doesNothingWhenNotAudited() {
        PlainEntity entity = new PlainEntity();
        handler.fillOnInsert(entity);
        assertThat(entity.name).isNull();
    }

    @Test
    void appendOnUpdate_addsUpdatedFieldsToUpdate() {
        Update update = new Update();
        handler.appendOnUpdate(update, AuditedEntity.class);
        Object setObject = update.getUpdateObject().get("$set");
        assertThat(setObject).isNotNull();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> setMap = (java.util.Map<String, Object>) setObject;
        assertThat(setMap).containsKey("updatedAt");
        assertThat(setMap).containsKey("updatedBy");
    }

    @Test
    void appendOnUpdate_doesNothingWhenNotAudited() {
        Update update = new Update();
        handler.appendOnUpdate(update, PlainEntity.class);
        assertThat(update.getUpdateObject()).isEmpty();
    }

    @Test
    void hasAuditFields_returnsTrueForAuditedEntity() {
        assertThat(handler.hasAuditFields(AuditedEntity.class)).isTrue();
    }

    @Test
    void hasAuditFields_returnsFalseForPlainEntity() {
        assertThat(handler.hasAuditFields(PlainEntity.class)).isFalse();
    }

    @AuditFields
    private static class AuditedEntity {
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;
    }

    private static class PlainEntity {
        private String name;
    }
}