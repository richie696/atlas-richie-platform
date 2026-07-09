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

import com.richie.component.mongodb.annotation.SoftDelete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import static org.assertj.core.api.Assertions.assertThat;

class SoftDeleteHandlerTest {

    private SoftDeleteHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SoftDeleteHandler();
    }

    @Test
    void addNotDeletedCriteria_whenSoftDeleteAnnotated() {
        Query query = new Query();
        handler.addNotDeletedCriteria(query, SoftDeleteEntity.class);
        assertThat(query.getQueryObject()).containsKey("deleted");
    }

    @Test
    void addNotDeletedCriteria_whenNotSoftDeleteAnnotated() {
        Query query = new Query();
        handler.addNotDeletedCriteria(query, PlainEntity.class);
        assertThat(query.getQueryObject()).isEmpty();
    }

    @Test
    void markAsDeleted_whenSoftDeleteAnnotated() {
        Update update = handler.markAsDeleted(SoftDeleteEntity.class);
        Object setObject = update.getUpdateObject().get("$set");
        assertThat(setObject).isNotNull();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> setMap = (java.util.Map<String, Object>) setObject;
        assertThat(setMap).containsKey("deleted");
        assertThat(setMap.get("deleted")).isEqualTo(true);
    }

    @Test
    void markAsDeleted_whenNotSoftDeleteAnnotated() {
        Update update = handler.markAsDeleted(PlainEntity.class);
        assertThat(update.getUpdateObject()).isEmpty();
    }

    @Test
    void getSoftDeleteField_whenSoftDeleteAnnotated() {
        assertThat(handler.getSoftDeleteField(SoftDeleteEntity.class)).isEqualTo("deleted");
    }

    @Test
    void getSoftDeleteField_whenNotSoftDeleteAnnotated() {
        assertThat(handler.getSoftDeleteField(PlainEntity.class)).isNull();
    }

    @SoftDelete
    private static class SoftDeleteEntity {
        private Boolean deleted;
    }

    private static class PlainEntity {
        private String name;
    }
}