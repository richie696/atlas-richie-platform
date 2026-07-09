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
package com.richie.component.dao.interceptor;

import com.richie.component.dao.config.DaoProperties;
import com.richie.contract.exception.BusinessException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchUpdateLimitInterceptorTest {

    @Test
    void beforeUpdate_rejectsCollectionExceedingLimit() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatement();

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b", "c")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量更新操作被拒绝");
    }

    @Test
    void beforeUpdate_allowsCollectionWithinLimit() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(5);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b")))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_treatsSingleEntityMapAsOneRecord() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("param1", new Object())))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_allowsNullParameterAsSingleUpdate() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, null))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_countsListKeyInParameterMap() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatement();

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, Map.of("list", List.of("a", "b", "c"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_countsCollectionKeyInParameterMap() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatement();

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, Map.of("collection", List.of("a", "b"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_countsCollKeyInParameterMap() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatement();

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, Map.of("coll", List.of("a", "b"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_countsNestedCollectionInMapValues() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatement();
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("payload", List.of("a", "b", "c"));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_treatsEtAndEwKeysAsSingleUpdate() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatement();

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("et", new Object())))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("ew", new Object())))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_detectsInClauseFromSqlAndIdsParameter() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t_user SET status = 1 WHERE id IN (?, ?, ?)");
        Map<String, Object> parameter = Map.of("ids", List.of(1, 2, 3));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_detectsIdListParameterForInClause() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatementWithSql("update t set x=1 where id in (#{idList})");
        Map<String, Object> parameter = Map.of("idList", List.of(10, 20));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_returnsOneWhenInClauseCannotBeResolved() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t SET x = 1 WHERE id IN (?)");

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("k", "v")))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_ignoresBoundSqlErrors() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(ms.getId()).thenReturn("com.example.UserMapper.update");
        when(ms.getBoundSql(any())).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, new Object()))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_ignoresNonUpdateStatements() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, List.of("a", "b", "c")))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_detectsIdCollectionParameterForInClause() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t SET x = 1 WHERE id IN (#{idCollection})");
        Map<String, Object> parameter = Map.of("idCollection", List.of(1, 2, 3));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_singleElementCollectionFallsThroughToExtractInClause() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t SET x = 1 WHERE id IN (#{idCollection})");
        Map<String, Object> parameter = Map.of("idCollection", List.of(1));

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, parameter))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_collectionKeyTakesPrecedenceOverListKey() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatement();
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("collection", List.of("a", "b", "c", "d", "e"));
        parameter.put("list", List.of("x"));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_skipsInClauseCheckWhenSqlHasNoIn() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t_user SET status = 1 WHERE id = ?");

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, Map.of("id", 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_ignoresBoundSqlException() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(ms.getId()).thenReturn("com.example.UserMapper.update");
        when(ms.getBoundSql(any())).thenThrow(new RuntimeException("bound sql error"));

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, "someParam"))
                .doesNotThrowAnyException();
    }

    @Test
    void beforeUpdate_handlesInCountGreaterThanZero() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(1);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t SET x = 1 WHERE id IN (?)");
        Map<String, Object> parameter = Map.of("idList", List.of(1, 2, 3));

        assertThatThrownBy(() -> interceptor.beforeUpdate(null, ms, parameter))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeUpdate_extractsInCountFromIdList() {
        BatchUpdateLimitInterceptor interceptor = interceptorWithLimit(2);
        MappedStatement ms = mappedUpdateStatementWithSql("UPDATE t SET x = 1 WHERE id IN (?)");
        Map<String, Object> parameter = Map.of("idList", List.of(1, 2));

        assertThatCode(() -> interceptor.beforeUpdate(null, ms, parameter))
                .doesNotThrowAnyException();
    }

    private static BatchUpdateLimitInterceptor interceptorWithLimit(int limit) {
        DaoProperties properties = new DaoProperties();
        properties.setBatchUpdateLimit(limit);
        return new BatchUpdateLimitInterceptor(properties);
    }

    private static MappedStatement mappedUpdateStatement() {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(ms.getId()).thenReturn("com.example.UserMapper.updateBatch");
        return ms;
    }

    private static MappedStatement mappedUpdateStatementWithSql(String sql) {
        MappedStatement ms = mock(MappedStatement.class);
        BoundSql boundSql = mock(BoundSql.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(ms.getId()).thenReturn("com.example.UserMapper.updateByIds");
        when(ms.getBoundSql(any())).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn(sql);
        return ms;
    }
}
