package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import com.mongodb.client.result.DeleteResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteBuilderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DeleteResult deleteResult;

    private EntityIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new EntityIntrospector();
    }

    @Test
    void eq_shouldAddCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, "x")
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void eq_withConditionFalse_shouldSkip() {
        when(deleteResult.getDeletedCount()).thenReturn(0L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(false, TestDoc::getId, "x")
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void eq_withNullValue_shouldSkip() {
        when(deleteResult.getDeletedCount()).thenReturn(0L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .eq(TestDoc::getId, null)
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void gt_shouldAddGtCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(2L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .gt(TestDoc::getAge, 18)
            .execute();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void gt_withNullValue_shouldSkip() {
        when(deleteResult.getDeletedCount()).thenReturn(0L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .gt(TestDoc::getAge, null)
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void ge_shouldAddGteCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(3L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .ge(TestDoc::getAge, 18)
            .execute();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void lt_shouldAddLtCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .lt(TestDoc::getAge, 100)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void le_shouldAddLteCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .le(TestDoc::getAge, 100)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void in_shouldAddInCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(2L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, java.util.List.of("A", "B"))
            .execute();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void in_withEmptyCollection_shouldSkip() {
        when(deleteResult.getDeletedCount()).thenReturn(0L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, java.util.Collections.emptyList())
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void in_withNullCollection_shouldSkip() {
        when(deleteResult.getDeletedCount()).thenReturn(0L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .in(TestDoc::getStatus, null)
            .execute();

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void nin_shouldAddNinCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .nin(TestDoc::getStatus, java.util.List.of("X", "Y"))
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void exists_shouldAddExistsCriteria() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .exists(TestDoc::getName)
            .execute();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void execute_withNoCriteria_shouldStillWork() {
        when(deleteResult.getDeletedCount()).thenReturn(5L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        long count = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector)
            .execute();

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void execute_calledTwice_shouldThrowIllegalState() {
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(Query.class), eq(TestDoc.class)))
            .thenReturn(deleteResult);

        DeleteBuilder<TestDoc> builder = new DeleteBuilder<>(TestDoc.class, mongoTemplate, introspector);
        builder.eq(TestDoc::getId, "x");
        builder.execute();

        assertThatThrownBy(() -> builder.execute())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only be executed once");
    }

    static class TestDoc {
        private String id;
        private String name;
        private int age;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
