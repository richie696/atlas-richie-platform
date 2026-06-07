package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.LambdaMeta;
import com.richie.component.mongodb.exception.MongodbException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateBuilderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UpdateResult updateResult;

    private EntityIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new EntityIntrospector();
    }

    @Test
    void eq_shouldAddCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void eq_withConditionFalse_shouldSkip() {
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(false, TestDoc::getId, "x")
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void eq_withNullValue_shouldSkip() {
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, null)
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void gt_shouldAddGtCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(2L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .gt(TestDoc::getAge, 18)
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void gt_withNullValue_shouldSkip() {
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .gt(TestDoc::getAge, null)
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void ge_shouldAddGteCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(3L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .ge(TestDoc::getAge, 18)
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void lt_shouldAddLtCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .lt(TestDoc::getAge, 100)
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void le_shouldAddLteCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .le(TestDoc::getAge, 100)
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void in_shouldAddInCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(2L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, java.util.List.of("A", "B"))
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void in_withEmptyCollection_shouldSkip() {
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, java.util.Collections.emptyList())
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void in_withNullCollection_shouldSkip() {
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, null)
            .set(TestDoc::getName, "test")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void nin_shouldAddNinCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .nin(TestDoc::getStatus, java.util.List.of("X", "Y"))
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void exists_shouldAddExistsCriteria() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .exists(TestDoc::getName)
            .set(TestDoc::getName, "exists")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void set_shouldAddSetUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .set(TestDoc::getName, "newName")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void inc_shouldAddIncUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .inc(TestDoc::getAge, 1)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void unset_shouldAddUnsetUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .unset(TestDoc::getName)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void push_shouldAddPushUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .push(TestDoc::getTags, "newTag")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void pull_shouldAddPullUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .pull(TestDoc::getTags, "oldTag")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void addToSet_shouldAddAddToSetUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .addToSet(TestDoc::getTags, "uniqueTag")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void rename_shouldAddRenameUpdate() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .rename(TestDoc::getOldName, TestDoc::getNewName)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void execute_withNoCriteria_shouldStillWork() {
        when(updateResult.getModifiedCount()).thenReturn(5L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        long count = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .set(TestDoc::getName, "updated")
            .execute();

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void execute_calledTwice_shouldThrowIllegalState() {
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(TestDoc.class)))
            .thenReturn(updateResult);

        UpdateBuilder<TestDoc> builder = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector);
        builder.eq(TestDoc::getId, "x");
        builder.set(TestDoc::getName, "test");
        builder.execute();

        assertThatThrownBy(() -> builder.execute())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only be executed once");
    }

    @Test
    void executeAndReturn_shouldReturnNewDocument() {
        TestDoc newDoc = new TestDoc();
        newDoc.setId("x");
        newDoc.setName("returned");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
            any(FindAndModifyOptions.class), eq(TestDoc.class)))
            .thenReturn(newDoc);

        TestDoc result = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .set(TestDoc::getName, "updated")
            .executeAndReturn();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("returned");
    }

    @Test
    void executeAndReturn_calledTwice_shouldThrowIllegalState() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
            any(FindAndModifyOptions.class), eq(TestDoc.class)))
            .thenReturn(new TestDoc());

        UpdateBuilder<TestDoc> builder = new UpdateBuilder<>(TestDoc.class, mongoTemplate, introspector);
        builder.eq(TestDoc::getId, "x");
        builder.set(TestDoc::getName, "test");
        builder.executeAndReturn();

        assertThatThrownBy(() -> builder.executeAndReturn())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only be executed once");
    }

    static class TestDoc {
        private String id;
        private String name;
        private String oldName;
        private String newName;
        private int age;
        private String status;
        private java.util.List<String> tags;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOldName() { return oldName; }
        public void setOldName(String oldName) { this.oldName = oldName; }
        public String getNewName() { return newName; }
        public void setNewName(String newName) { this.newName = newName; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.util.List<String> getTags() { return tags; }
        public void setTags(java.util.List<String> tags) { this.tags = tags; }
    }
}
