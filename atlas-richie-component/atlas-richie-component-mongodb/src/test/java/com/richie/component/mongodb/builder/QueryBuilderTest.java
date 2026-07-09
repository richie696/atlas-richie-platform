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
package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryBuilderTest {

    private static ThreadLocalHolder holder;

    @BeforeAll
    static void initContext() {
        holder = new ThreadLocalHolder();
        TenantContext.init(holder);
    }

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private EntityIntrospector entityIntrospector;

    private QueryBuilder<TestEntity> builder;

    @BeforeEach
    void setUp() {
        builder = new QueryBuilder<>(TestEntity.class, mongoTemplate, entityIntrospector);
    }

    @Test
    void eq_shouldAddCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.eq(TestEntity::getName, "test");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void eq_withConditionFalse_shouldSkip() {
        builder.eq(false, TestEntity::getName, "test");
        List<TestEntity> result = builder.list();
        assertThat(result).isEmpty();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void ne_shouldAddNeCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.ne(TestEntity::getName, "test");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void gt_shouldAddGtCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.gt(TestEntity::getAge, 18);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void ge_shouldAddGteCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.ge(TestEntity::getAge, 18);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void lt_shouldAddLtCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.lt(TestEntity::getAge, 100);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void le_shouldAddLteCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.le(TestEntity::getAge, 100);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void between_shouldAddRangeCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.between(TestEntity::getAge, 18, 100);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void like_shouldAddRegexCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.like(TestEntity::getName, "test.*");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void in_shouldAddInCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("status");
        builder.in(TestEntity::getStatus, List.of("A", "B"));
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void nin_shouldAddNinCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("status");
        builder.nin(TestEntity::getStatus, List.of("A", "B"));
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void exists_shouldAddExistsCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.exists(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void isNull_shouldAddNullCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.isNull(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void isNotNull_shouldAddNeNullCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.isNotNull(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void orderByAsc_shouldAddSort() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.orderByAsc(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void orderByDesc_shouldAddSort() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.orderByDesc(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void select_shouldIncludeFields() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.select(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void page_shouldSetPageable() {
        builder.page(1, 10);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void skip_shouldSetSkip() {
        builder.skip(5);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void limit_shouldSetLimit() {
        builder.limit(10);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void list_shouldReturnResults() {
        List<TestEntity> expected = List.of(new TestEntity());
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(expected);
        List<TestEntity> result = builder.list();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void one_shouldReturnSingleOrNull() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of(entity));
        TestEntity result = builder.one();
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void one_whenEmpty_shouldReturnNull() {
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of());
        TestEntity result = builder.one();
        assertThat(result).isNull();
    }

    @Test
    void oneOpt_shouldReturnOptional() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of(entity));
        assertThat(builder.oneOpt()).isPresent();
    }

    @Test
    void oneOrThrow_whenEmpty_shouldThrow() {
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of());
        assertThatThrownBy(() -> builder.oneOrThrow(IllegalStateException::new)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void count_shouldReturnCount() {
        when(mongoTemplate.count(any(Query.class), eq(TestEntity.class))).thenReturn(5L);
        long count = builder.count();
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void pageResult_shouldReturnPageResult() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.count(any(Query.class), eq(TestEntity.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of(entity));
        PageResult<TestEntity> result = builder.page(1, 10).pageResult();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void doubleExecute_shouldThrow() {
        builder.list();
        assertThatThrownBy(() -> builder.list()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void between_withBothNull_shouldSkip() {
        builder.between(TestEntity::getAge, null, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void between_withOnlyFrom_shouldApplyGte() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.between(TestEntity::getAge, 18, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void between_withOnlyTo_shouldApplyLte() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("age");
        builder.between(TestEntity::getAge, null, 100);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void like_withNullPattern_shouldSkip() {
        builder.like(TestEntity::getName, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void like_withEmptyPattern_shouldSkip() {
        builder.like(TestEntity::getName, "");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void select_withStringArray_shouldIncludeFields() {
        builder.select("name", "age");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void orderByAsc_withString_shouldAddSort() {
        builder.orderByAsc("name");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void orderByDesc_withString_shouldAddSort() {
        builder.orderByDesc("name");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void and_shouldChainCriteria() {
        when(entityIntrospector.resolveFieldName(any())).thenReturn("name");
        builder.and(TestEntity::getName);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void or_withNullOther_shouldReturnThis() {
        builder.or(null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void or_withOtherHavingNoCriteria_shouldReturnThis() {
        builder.or(new QueryBuilder<>(TestEntity.class, mongoTemplate, entityIntrospector));
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void pageResult_withNoPageSet_shouldUseDefaultValues() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.count(any(Query.class), eq(TestEntity.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of(entity));
        PageResult<TestEntity> result = builder.pageResult();
        assertThat(result.getPageNum()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(1);
    }

    @Test
    void oneOrThrow_whenNotEmpty_shouldReturnEntity() {
        TestEntity entity = new TestEntity();
        when(mongoTemplate.find(any(Query.class), eq(TestEntity.class))).thenReturn(List.of(entity));
        TestEntity result = builder.oneOrThrow(IllegalStateException::new);
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void count_withNoQuery_shouldReturnZero() {
        when(mongoTemplate.count(any(Query.class), eq(TestEntity.class))).thenReturn(0L);
        long count = builder.count();
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void eq_withNullValue_shouldSkip() {
        builder.eq(TestEntity::getName, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void gt_withNullValue_shouldSkip() {
        builder.gt(TestEntity::getAge, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void ge_withNullValue_shouldSkip() {
        builder.ge(TestEntity::getAge, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void lt_withNullValue_shouldSkip() {
        builder.lt(TestEntity::getAge, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void le_withNullValue_shouldSkip() {
        builder.le(TestEntity::getAge, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void ne_withNullValue_shouldSkip() {
        builder.ne(TestEntity::getName, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void in_withNullCollection_shouldSkip() {
        builder.in(TestEntity::getStatus, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void in_withEmptyCollection_shouldSkip() {
        builder.in(TestEntity::getStatus, List.of());
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void nin_withNullCollection_shouldSkip() {
        builder.nin(TestEntity::getStatus, null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void nin_withEmptyCollection_shouldSkip() {
        builder.nin(TestEntity::getStatus, List.of());
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void bypassTenant_shouldSetFlag() {
        builder.bypassTenant();
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void ignoreSoftDelete_shouldSetFlag() {
        builder.ignoreSoftDelete();
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void applyAnnotationFilters_withSoftDeleteField_shouldAddCriteria() {
        when(entityIntrospector.getSoftDeleteField(TestEntity.class)).thenReturn("deleted");
        when(entityIntrospector.getTenantField(TestEntity.class)).thenReturn(null);
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void applyAnnotationFilters_withTenantFieldAndContext_shouldAddCriteria() {
        when(entityIntrospector.getSoftDeleteField(TestEntity.class)).thenReturn(null);
        when(entityIntrospector.getTenantField(TestEntity.class)).thenReturn("tenantId");
        holder.set(new TenantPrincipal().setTenantId(1L));
        try {
            builder.list();
            verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void applyAnnotationFilters_withTenantFieldAndNoContext_shouldNotAddTenantCriteria() {
        when(entityIntrospector.getSoftDeleteField(TestEntity.class)).thenReturn(null);
        when(entityIntrospector.getTenantField(TestEntity.class)).thenReturn("tenantId");
        builder.list();
        verify(mongoTemplate).find(any(Query.class), eq(TestEntity.class));
    }

    static class TestEntity {
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
