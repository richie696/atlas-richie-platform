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