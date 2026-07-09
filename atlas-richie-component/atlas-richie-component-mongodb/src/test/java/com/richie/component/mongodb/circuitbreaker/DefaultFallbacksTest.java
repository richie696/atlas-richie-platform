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
package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;
import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.PageResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFallbacksTest {

    @Test
    void query_shouldReturnEmptyList() {
        List<?> result = DefaultFallbacks.query(String.class);
        assertThat(result).isEmpty();
    }

    @Test
    void count_shouldReturnZero() {
        Long result = DefaultFallbacks.count(String.class);
        assertThat(result).isZero();
    }

    @Test
    void one_shouldReturnNull() {
        String result = DefaultFallbacks.one(String.class);
        assertThat(result).isNull();
    }

    @Test
    void oneOpt_shouldReturnEmptyOptional() {
        Optional<?> result = DefaultFallbacks.oneOpt(String.class);
        assertThat(result).isEmpty();
    }

    @Test
    void pageResult_shouldReturnEmptyPageResult() {
        PageResult<?> result = DefaultFallbacks.pageResult(String.class);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void updateExecute_shouldReturnZero() {
        long result = DefaultFallbacks.updateExecute();
        assertThat(result).isZero();
    }

    @Test
    void deleteExecute_shouldReturnZero() {
        long result = DefaultFallbacks.deleteExecute();
        assertThat(result).isZero();
    }

    @Test
    void save_shouldReturnNull() {
        Object result = DefaultFallbacks.save();
        assertThat(result).isNull();
    }

    @Test
    void insert_shouldReturnNull() {
        Object result = DefaultFallbacks.insert();
        assertThat(result).isNull();
    }

    @Test
    void insertAll_shouldReturnEmptyList() {
        List<?> result = DefaultFallbacks.insertAll();
        assertThat(result).isEmpty();
    }

    @Test
    void findById_shouldReturnEmptyOptional() {
        Optional<?> result = DefaultFallbacks.findById();
        assertThat(result).isEmpty();
    }

    @Test
    void existsById_shouldReturnFalse() {
        boolean result = DefaultFallbacks.existsById();
        assertThat(result).isFalse();
    }

    @Test
    void deleteById_shouldReturnFalse() {
        boolean result = DefaultFallbacks.deleteById();
        assertThat(result).isFalse();
    }

    @Test
    void dropCollection_shouldReturnFalse() {
        boolean result = DefaultFallbacks.dropCollection();
        assertThat(result).isFalse();
    }

    @Test
    void queryBuilder_shouldReturnDegradedBuilder() {
        QueryBuilder<String> builder = DefaultFallbacks.queryBuilder(String.class);
        assertThat(builder).isNotNull();
        List<String> result = builder.list();
        assertThat(result).isEmpty();
    }

    @Test
    void updateBuilder_shouldReturnDegradedBuilder() {
        UpdateBuilder<String> builder = DefaultFallbacks.updateBuilder(String.class);
        assertThat(builder).isNotNull();
        long result = builder.execute();
        assertThat(result).isZero();
    }

    @Test
    void deleteBuilder_shouldReturnDegradedBuilder() {
        DeleteBuilder<String> builder = DefaultFallbacks.deleteBuilder(String.class);
        assertThat(builder).isNotNull();
        long result = builder.execute();
        assertThat(result).isZero();
    }

    @Test
    void degradedQueryBuilder_one_shouldReturnNull() {
        QueryBuilder<String> builder = DefaultFallbacks.queryBuilder(String.class);
        assertThat(builder.one()).isNull();
    }

    @Test
    void degradedQueryBuilder_oneOpt_shouldReturnEmpty() {
        QueryBuilder<String> builder = DefaultFallbacks.queryBuilder(String.class);
        assertThat(builder.oneOpt()).isEmpty();
    }

    @Test
    void degradedQueryBuilder_count_shouldReturnZero() {
        QueryBuilder<String> builder = DefaultFallbacks.queryBuilder(String.class);
        assertThat(builder.count()).isZero();
    }

    @Test
    void degradedQueryBuilder_pageResult_shouldReturnEmpty() {
        QueryBuilder<String> builder = DefaultFallbacks.queryBuilder(String.class);
        PageResult<String> result = builder.pageResult();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void degradedUpdateBuilder_executeAndReturn_shouldReturnNull() {
        UpdateBuilder<String> builder = DefaultFallbacks.updateBuilder(String.class);
        assertThat(builder.executeAndReturn()).isNull();
    }
}
